#!/data/data/com.termux/files/usr/bin/bash
# KimiClaw install script — single source of truth
# Called by: GUI (ProcessBuilder) and terminal (profile.d)
# Outputs structured lines for GUI progress parsing.

# CRITICAL: Clear all environment variables that might contain wrong paths from bootstrap
unset SSL_CERT_FILE
unset SSL_CERT_DIR
unset CURL_CA_BUNDLE
unset REQUESTS_CA_BUNDLE
unset NODE_EXTRA_CA_CERTS
unset GIT_SSH
unset GIT_SSH_COMMAND

# Export PATH explicitly to ensure all commands are found
export PATH="/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets:/data/data/com.termux/files/usr/libexec/git-core:$PATH"
export PREFIX="/data/data/com.termux/files/usr"

# Ensure HOME directory exists
mkdir -p "$HOME"

# Create git config file directly with HTTPS substitution
# Use insteadOf (case sensitive) - uppercase O is required
cat > "$HOME/.gitconfig" << 'GITCONFIG'
[url "https://github.com/whiskeysockets/"]
    insteadOf = ssh://git@github.com/whiskeysockets/
[url "https://github.com/"]
    insteadOf = git@github.com:
[url "https://github.com/"]
    insteadOf = ssh://git@github.com/
[url "https://"]
    insteadOf = ssh://
[http]
    sslCAInfo = /data/data/com.termux/files/usr/etc/tls/cert.pem
    sslVerify = true
GITCONFIG

# Set correct SSL cert path for all tools
export SSL_CERT_FILE="/data/data/com.termux/files/usr/etc/tls/cert.pem"
export CURL_CA_BUNDLE="/data/data/com.termux/files/usr/etc/tls/cert.pem"

LOGFILE="$HOME/kimiclaw-install.log"
exec > >(tee -a "$LOGFILE") 2>&1
echo "=== KimiClaw install started: $(date) ==="

MARKER="$HOME/.kimiclaw_installed"

if [ -f "$MARKER" ]; then
    echo "KIMICLAW_ALREADY_INSTALLED"
    exit 0
fi

echo "Setting up environment"
chmod +x $PREFIX/bin/* 2>/dev/null
chmod +x $PREFIX/lib/node_modules/.bin/* 2>/dev/null
chmod +x $PREFIX/lib/node_modules/npm/bin/* 2>/dev/null

# Configure npm
NODE="$PREFIX/bin/node"
NPM_CLI="$PREFIX/lib/node_modules/npm/bin/npm-cli.js"

"$NODE" "$NPM_CLI" config set git-tag-version false 2>/dev/null || true
"$NODE" "$NPM_CLI" config set legacy-peer-deps true 2>/dev/null || true
"$NODE" "$NPM_CLI" config set registry "https://registry.npmjs.org/" 2>/dev/null || true

# Show git config for debugging
echo "Git config:"
cat "$HOME/.gitconfig"

# Generate SSH host keys if missing (openssh.postinst equivalent)
mkdir -p $PREFIX/var/empty
mkdir -p $HOME/.ssh
touch $HOME/.ssh/authorized_keys
chmod 700 $HOME/.ssh
chmod 600 $HOME/.ssh/authorized_keys
for a in rsa ecdsa ed25519; do
    KEYFILE="$PREFIX/etc/ssh/ssh_host_${a}_key"
    test ! -f "$KEYFILE" && ssh-keygen -N '' -t $a -f "$KEYFILE" >/dev/null 2>&1
done
# Generate random SSH password
SSH_PASS=$(head -c 12 /dev/urandom | base64 | tr -d '/+=' | head -c 12)
printf '%s\n%s\n' "$SSH_PASS" "$SSH_PASS" | passwd >/dev/null 2>&1
echo "$SSH_PASS" > "$HOME/.ssh_password"
chmod 600 "$HOME/.ssh_password"
# Create required OpenClaw directories
mkdir -p $HOME/.openclaw/agents/main/agent
mkdir -p $HOME/.openclaw/agents/main/sessions
mkdir -p $HOME/.openclaw/credentials
# Start sshd (port 8022)
if ! pgrep -x sshd >/dev/null 2>&1; then
    sshd 2>/dev/null
fi

echo "Verifying Node.js"
NODE_V=$("$NODE" --version 2>&1)
NPM_V=$("$NODE" "$NPM_CLI" --version 2>&1)
if ! command -v node >/dev/null 2>&1 || [ ! -f "$NPM_CLI" ]; then
    echo "KIMICLAW_ERROR:Node.js or npm not found. Bootstrap may be corrupted."
    exit 1
fi
echo "KIMICLAW_INFO:Node $NODE_V, npm $NPM_V"

echo "Installing OpenClaw"

# Check network connectivity first
echo "Checking network connectivity..."
if ! curl -s --max-time 10 -I https://github.com >/dev/null 2>&1; then
    echo "Network check failed, trying with explicit DNS..."
    echo "nameserver 8.8.8.8" > "$PREFIX/etc/resolv.conf"
    if ! curl -s --max-time 10 -I https://github.com >/dev/null 2>&1; then
        echo "KIMICLAW_ERROR:Cannot connect to GitHub. Please check network connection and try again."
        exit 1
    fi
fi
echo "Network OK"

# Clean npm cache to avoid issues with failed git clones
echo "Installing OpenClaw..."
rm -rf "$HOME/.npm/_cacache/tmp" 2>/dev/null || true
rm -rf $PREFIX/lib/node_modules/openclaw 2>/dev/null
NPM_OUTPUT=$("$NODE" "$NPM_CLI" install -g openclaw@latest --ignore-scripts --force 2>&1)
NPM_EXIT=$?
if [ $NPM_EXIT -eq 0 ]; then
    # Create a stable openclaw wrapper (npm-generated shim can be broken on Android/proot)
    cat > $PREFIX/bin/openclaw <<'KIMICLAW_OPENCLAW_WRAPPER'
#!/data/data/com.termux/files/usr/bin/bash
PREFIX="$(cd "$(dirname "$0")/.." && pwd)"
ENTRY=""
for CANDIDATE in \
  "$PREFIX/lib/node_modules/openclaw/dist/cli.js" \
  "$PREFIX/lib/node_modules/openclaw/bin/openclaw.js" \
  "$PREFIX/lib/node_modules/openclaw/dist/index.js"; do
  if [ -f "$CANDIDATE" ]; then
    ENTRY="$CANDIDATE"
    break
  fi
done
if [ -z "$ENTRY" ]; then
  echo "openclaw entrypoint not found under $PREFIX/lib/node_modules/openclaw" >&2
  exit 127
fi
export SSL_CERT_FILE="$PREFIX/etc/tls/cert.pem"
export NODE_OPTIONS="--dns-result-order=ipv4first"
exec "$PREFIX/bin/termux-chroot" "$PREFIX/bin/node" "$ENTRY" "$@"
KIMICLAW_OPENCLAW_WRAPPER

    # Install sharp-wasm32 for Android image processing support
    echo "Installing image processing support"
    cd $PREFIX/lib/node_modules/openclaw
    SHARP_OUTPUT=$("$NODE" "$NPM_CLI" install --force --cpu=wasm32 @img/sharp-wasm32 2>&1)
    SHARP_EXIT=$?
    if [ $SHARP_EXIT -eq 0 ]; then
        echo "KIMICLAW_INFO:Image processing support installed"
    else
        echo "KIMICLAW_WARN:Failed to install image processing support (exit $SHARP_EXIT): $SHARP_OUTPUT"
    fi

    chmod 755 $PREFIX/bin/openclaw
    touch "$MARKER"
    echo "KIMICLAW_COMPLETE"
else
    echo "KIMICLAW_ERROR:npm install failed (exit $NPM_EXIT): $NPM_OUTPUT"
    exit 1
fi
