# Snapdragon Game Super Resolution 1 in XenDroid

Source: SnapdragonStudios/snapdragon-gsr, revision `d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e`,
`sgsr/v1/include/glsl/sgsr1_shader_mobile.frag` (operation mode RGBA). License: BSD-3-Clause,
in `LICENSE` here; the notice is kept in the ported shader and shipped in the APK under
`assets/engine-licenses/sgsr-BSD-3-Clause.txt`.

What XenDroid changed:

- The shader is rewritten in Xenia's XESL (`xenia/src/xenia/ui/shaders/guest_output_sgsr.xesli`)
  as a guest output paint effect: the output pixel comes from the presenter's rectangle
  vertex shader and push constants instead of a varying and a uniform, and an optional dither
  variant applies Xenia's 8bpc blue-noise dither after it, like the other final effects.
- The algorithm, its constants (edge threshold 8/255, edge sharpness 2.0, luma steps limited
  to 23/255) and the gather pattern are the reference's.
- It is one effect among Xenia's ("sgsr" in `postprocess_scaling_and_sharpening`, or the
  in-game scaling choice): one pass to the output size when the guest output is smaller,
  plain bilinear otherwise. Experimental: its cost on a phone has not been measured yet.
