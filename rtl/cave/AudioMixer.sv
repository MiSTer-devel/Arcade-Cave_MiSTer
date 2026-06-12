// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Signed audio mixer with fixed-point gain and 16-bit output clipping.
module AudioMixer (
  input         clock,
  input         io_pwrinst2,
  input  [13:0] io_in_4,
  input  [13:0] io_in_3,
  input  [15:0] io_in_2,
  input  [15:0] io_in_1,
  input  [15:0] io_in_0,
  output [15:0] io_out
);
  localparam signed [28:0] MIN_SAMPLE = -29'sd32768;
  localparam signed [28:0] MAX_SAMPLE =  29'sd32767;

  wire signed [18:0] channel_1_sample = $signed({{3{io_in_1[15]}}, io_in_1});
  wire signed [21:0] channel_3_sample = $signed({{6{io_in_3[13]}}, io_in_3, 2'b00});

  wire signed [18:0] channel_1_gain =
    channel_1_sample + (channel_1_sample <<< 1);
  wire signed [21:0] channel_3_gain =
    (channel_3_sample <<< 4) + (channel_3_sample <<< 3) + (channel_3_sample <<< 1);

  wire signed [25:0] legacy_mix_sum =
    $signed({{6{io_in_0[15]}}, io_in_0, 4'b0000})
    + $signed({{7{channel_1_gain[18]}}, channel_1_gain})
    + $signed({{6{io_in_2[15]}}, io_in_2, 4'b0000})
    + $signed({{4{channel_3_gain[21]}}, channel_3_gain})
    + $signed({{6{io_in_4[13]}}, io_in_4, 6'b000000});
  wire signed [28:0] legacy_mix_ext = {{3{legacy_mix_sum[25]}}, legacy_mix_sum};

  wire signed [18:0] pwrinst2_psg_sample = $signed({3'b000, io_in_1}) - 19'sd16384;
  wire signed [18:0] pwrinst2_fm_sample = $signed({{3{io_in_2[15]}}, io_in_2});
  wire signed [18:0] pwrinst2_oki0_sample = $signed({{3{io_in_3[13]}}, io_in_3, 2'b00});
  wire signed [18:0] pwrinst2_oki1_sample = $signed({{3{io_in_4[13]}}, io_in_4, 2'b00});
  wire signed [22:0] pwrinst2_psg_gain = $signed({{4{pwrinst2_psg_sample[18]}}, pwrinst2_psg_sample}) <<< 1;
  wire signed [22:0] pwrinst2_fm_gain = $signed({{4{pwrinst2_fm_sample[18]}}, pwrinst2_fm_sample}) <<< 4;
  wire signed [22:0] pwrinst2_oki0_gain = $signed({{4{pwrinst2_oki0_sample[18]}}, pwrinst2_oki0_sample});
  wire signed [22:0] pwrinst2_oki1_gain = $signed({{4{pwrinst2_oki1_sample[18]}}, pwrinst2_oki1_sample});
  wire signed [24:0] pwrinst2_mix_sum =
    $signed({{2{pwrinst2_psg_gain[22]}}, pwrinst2_psg_gain})
    + $signed({{2{pwrinst2_fm_gain[22]}}, pwrinst2_fm_gain})
    + $signed({{2{pwrinst2_oki0_gain[22]}}, pwrinst2_oki0_gain})
    + $signed({{2{pwrinst2_oki1_gain[22]}}, pwrinst2_oki1_gain});
  wire signed [28:0] pwrinst2_mix_ext = {{4{pwrinst2_mix_sum[24]}}, pwrinst2_mix_sum};

  wire signed [28:0] scaled_sum =
    io_pwrinst2 ? (pwrinst2_mix_ext >>> 1) : (legacy_mix_ext >>> 4);
  wire signed [28:0] clipped_low = scaled_sum < MIN_SAMPLE ? MIN_SAMPLE : scaled_sum;
  wire signed [28:0] clipped = clipped_low < MAX_SAMPLE ? clipped_low : MAX_SAMPLE;

  reg signed [28:0] audio_reg;

  always @(posedge clock)
    audio_reg <= clipped;

  assign io_out = audio_reg[15:0];
endmodule
