# KimiClaw environment setup
export TMPDIR=$PREFIX/tmp
mkdir -p $TMPDIR 2>/dev/null

# `openclaw` is installed as a wrapper that already runs under `termux-chroot`.
# Avoid nesting proot/termux-chroot which can make commands extremely slow.

# Auto-start sshd if not running
if ! pgrep -x sshd >/dev/null 2>&1; then
    sshd 2>/dev/null
fi

# Run install if not done yet
if [ ! -f "$HOME/.kimiclaw_installed" ]; then
    echo "\U0001F4A7 Setting up KimiClaw..."
    bash $PREFIX/share/kimiclaw/install.sh
fi
