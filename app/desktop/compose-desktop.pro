# Desktop release (packageReleaseMsi) ProGuard rules.

# Haze's skiko RenderEffect helper references a Skiko ShaderBrush.createShader overload that the
# resolved Compose/Skiko version does not expose, so ProGuard reports one unresolved reference and
# — because Compose Desktop treats ProGuard warnings as fatal — the release build fails. The desktop
# glass no longer uses Haze's refraction path (it uses the material engine plus a Skia RuntimeShader),
# so that code is never invoked here; suppress the warning rather than pin an older Haze/Skiko pair.
-dontwarn dev.chrisbanes.haze.**
