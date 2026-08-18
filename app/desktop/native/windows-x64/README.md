# Native libraries, Windows x64

`libmpv-2.dll` belongs here. It is deliberately not committed: it is a 64 bit binary of
tens of megabytes with its own release cadence.

Source the official Windows build from
<https://sourceforge.net/projects/mpv-player-windows/files/libmpv/>, take
`libmpv-2.dll` from the archive and place it in this directory.

At start up `app/desktop` registers this directory on the JNA library search path. At
package time Compose Desktop copies it into the MSI through `appResourcesRootDir`, so
the installed application carries the DLL and the user installs nothing separately.

See CLAUDE.md section 4, "Native library for Windows".
