// Port of liquid-glass-react/src/index.tsx GlassFilter (MIT, MAX ROVENSKY).
// See assets/liquid-glass-react-LICENSE.txt. Foreground content is not filtered.
#include <flutter/runtime_effect.glsl>

uniform vec2 uInputSize; // Supplied by ImageFilter.shader on Impeller.
uniform vec4 uBounds;   // Paint bounds in logical pixels.
uniform float uPixelRatio;
uniform float uDisplacement;
uniform float uAberration;
uniform float uSaturation;
uniform sampler2D uInput;
uniform sampler2D uMap;
out vec4 fragColor;

vec4 sourceAt(vec2 pixel) {
  vec2 inset = 0.5 / uInputSize;
  vec2 uv = clamp(pixel / uInputSize, inset, vec2(1.0) - inset);
  #ifdef IMPELLER_TARGET_OPENGLES
    uv.y = 1.0 - uv.y;
  #endif
  vec4 sourceColor = texture(uInput, uv);
  vec3 rgb = sourceColor.rgb / max(sourceColor.a, 0.0001);
  // backdrop-filter blur() saturate() runs before the SVG displacement filter.
  float luminance = dot(rgb, vec3(0.213, 0.715, 0.072));
  rgb = clamp(mix(vec3(luminance), rgb, uSaturation), 0.0, 1.0);
  return vec4(rgb * sourceColor.a, sourceColor.a);
}

bool insideContent(vec2 local) {
  return local.x >= 0.0 && local.y >= 0.0 &&
      local.x <= uBounds.z && local.y <= uBounds.w;
}

// Samples the displaced channel, but falls back to the undistorted center
// whenever the offset lands outside the panel content (or in the snapshot's
// transparent padding). Relying on alpha alone fails when the snapshot has no
// padding and the clamped edge texel is opaque: chromatic aberration then leaks
// a green/black seam on the right edge in dark mode.
vec4 sampleChannel(vec2 pixel, vec2 local, vec4 fallback) {
  if (!insideContent(local)) return fallback;
  vec4 sampled = sourceAt(pixel);
  return sampled.a <= 0.0001 ? fallback : sampled;
}

void main() {
  vec2 pixel = FlutterFragCoord().xy;
  vec2 local = pixel / uPixelRatio - uBounds.xy;
  // SVG feImage preserveAspectRatio="xMidYMid slice"; all source maps are square.
  vec2 mapUV = (local - uBounds.zw * 0.5) / max(uBounds.z, uBounds.w) + 0.5;
  vec4 displacement = texture(uMap, clamp(mapUV, vec2(0.0), vec2(1.0)));
  vec2 delta = (displacement.rb - 0.5) * uDisplacement * uPixelRatio;
  vec2 deltaLocal = delta / uPixelRatio;

  vec4 center = sourceAt(pixel);

  vec4 red = sampleChannel(pixel - delta, local - deltaLocal, center);
  vec4 green = sampleChannel(
      pixel + delta * (-1.0 - uAberration * 0.05),
      local + deltaLocal * (-1.0 - uAberration * 0.05),
      center);
  vec4 blue = sampleChannel(
      pixel + delta * (-1.0 - uAberration * 0.1),
      local + deltaLocal * (-1.0 - uAberration * 0.1),
      center);

  vec3 rgb = vec3(red.r / max(red.a, 0.0001),
                  green.g / max(green.a, 0.0001),
                  blue.b / max(blue.a, 0.0001));
  fragColor = vec4(rgb * center.a, center.a);
}
