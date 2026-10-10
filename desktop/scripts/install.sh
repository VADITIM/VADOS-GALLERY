#!/usr/bin/env bash
# Builds a release and installs it for the current user: the binary to ~/.local/bin, the launcher entry, the icon and the D-Bus service to ~/.local/share.
# Set VADOS_APPS=/some/dir to put the binary there instead of ~/.local/bin (the launcher entry and the service then point at it).
# Pass --default to also make it what opens photos and videos everywhere (xdg-mime), VAD/OS Files included.
set -euo pipefail
cd "$(dirname "$0")/.."
cmake -B build-release -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build-release
cmake --install build-release --prefix "$HOME/.local"
binary="$HOME/.local/bin/vados-gallery"
if [[ -n "${VADOS_APPS:-}" ]]; then
    mkdir -p "$VADOS_APPS"
    install -m755 build-release/vados-gallery "$VADOS_APPS/vados-gallery"
    binary="$VADOS_APPS/vados-gallery"
    sed -i "s|^Exec=vados-gallery|Exec=$binary|" "$HOME/.local/share/applications/vados-gallery.desktop"
fi
# D-Bus activation goes through systemd, which does not search ~/.local/bin; the service file names the binary by its full path.
sed -i "s|^Exec=.*vados-gallery --dbus-service|Exec=$binary --dbus-service|" "$HOME/.local/share/dbus-1/services/org.vados.Gallery.service"
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
gtk-update-icon-cache -q "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
if [[ "${1:-}" == "--default" ]]; then
    types=$(sed -n 's/^MimeType=//p' packaging/vados-gallery.desktop | tr ';' ' ')
    # shellcheck disable=SC2086
    xdg-mime default vados-gallery.desktop $types
    echo "VAD/OS Gallery now opens photos and videos."
fi
echo "Installed to $binary"
