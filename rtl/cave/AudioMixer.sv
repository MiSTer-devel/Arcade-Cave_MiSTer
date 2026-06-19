// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Signed audio mixer with fixed-point gain and 16-bit output clipping.
module AudioMixer (
  input         clock,
  input         io_pwrinst2,
  input  [2:0]  io_pwrinst2_oki0_level,
  input  [2:0]  io_pwrinst2_oki1_level,
  input         io_pwrinst2_headroom,
  input  [2:0]  io_pwrinst2_psg_level,
  input  [2:0]  io_pwrinst2_fm_level,
  input  [13:0] io_in_4,
  input  [13:0] io_in_3,
  input  [15:0] io_in_2,
  input  [15:0] io_in_1,
  input  [15:0] io_in_0,
  output [15:0] io_out
);
  localparam signed [34:0] MIN_SAMPLE = -35'sd32768;
  localparam signed [34:0] MAX_SAMPLE =  35'sd32767;

  function automatic signed [31:0] pwrinst2_apply_trim;
    input signed [31:0] base;
    input [2:0] level;
    begin
      case (level)
        3'd1: pwrinst2_apply_trim = (base >>> 1) + (base >>> 2);
        3'd2: pwrinst2_apply_trim = base >>> 1;
        3'd3: pwrinst2_apply_trim = base + (base >>> 2);
        3'd4: pwrinst2_apply_trim = base + (base >>> 1);
        3'd5: pwrinst2_apply_trim = base <<< 1;
        3'd6: pwrinst2_apply_trim = (base <<< 1) + (base >>> 1);
        3'd7: pwrinst2_apply_trim = (base <<< 1) + base;
        default: pwrinst2_apply_trim = base;
      endcase
    end
  endfunction

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
  wire signed [31:0] pwrinst2_oki0_base =
    $signed({{13{pwrinst2_oki0_sample[18]}}, pwrinst2_oki0_sample});
  wire signed [31:0] pwrinst2_oki1_base =
    $signed({{13{pwrinst2_oki1_sample[18]}}, pwrinst2_oki1_sample});
  wire signed [31:0] pwrinst2_psg_base =
    $signed({{13{pwrinst2_psg_sample[18]}}, pwrinst2_psg_sample});
  wire signed [31:0] pwrinst2_fm_base =
    $signed({{13{pwrinst2_fm_sample[18]}}, pwrinst2_fm_sample});
  wire signed [31:0] pwrinst2_psg_anchor = pwrinst2_psg_base <<< 1;
  wire signed [31:0] pwrinst2_fm_anchor = pwrinst2_fm_base <<< 4;
  wire signed [31:0] pwrinst2_oki0_anchor = pwrinst2_oki0_base <<< 3;
  wire signed [31:0] pwrinst2_oki1_anchor =
    (pwrinst2_oki1_base <<< 2) + (pwrinst2_oki1_base <<< 1);
  wire signed [31:0] pwrinst2_psg_gain = pwrinst2_apply_trim(pwrinst2_psg_anchor, io_pwrinst2_psg_level);
  wire signed [31:0] pwrinst2_fm_gain = pwrinst2_apply_trim(pwrinst2_fm_anchor, io_pwrinst2_fm_level);
  wire signed [31:0] pwrinst2_oki0_gain = pwrinst2_apply_trim(pwrinst2_oki0_anchor, io_pwrinst2_oki0_level);
  wire signed [31:0] pwrinst2_oki1_gain = pwrinst2_apply_trim(pwrinst2_oki1_anchor, io_pwrinst2_oki1_level);
  wire signed [34:0] pwrinst2_mix_sum =
    $signed({{3{pwrinst2_psg_gain[31]}}, pwrinst2_psg_gain})
    + $signed({{3{pwrinst2_fm_gain[31]}}, pwrinst2_fm_gain})
    + $signed({{3{pwrinst2_oki0_gain[31]}}, pwrinst2_oki0_gain})
    + $signed({{3{pwrinst2_oki1_gain[31]}}, pwrinst2_oki1_gain});
  wire signed [34:0] pwrinst2_scaled_sum =
    io_pwrinst2_headroom ? (pwrinst2_mix_sum >>> 2) : (pwrinst2_mix_sum >>> 1);

  wire signed [28:0] legacy_scaled_sum = legacy_mix_ext >>> 4;
  wire signed [34:0] scaled_sum =
    io_pwrinst2 ? pwrinst2_scaled_sum :
                  $signed({{6{legacy_scaled_sum[28]}}, legacy_scaled_sum});
  wire signed [34:0] clipped_low = scaled_sum < MIN_SAMPLE ? MIN_SAMPLE : scaled_sum;
  wire signed [34:0] clipped = clipped_low < MAX_SAMPLE ? clipped_low : MAX_SAMPLE;

  reg signed [34:0] audio_reg;

  always @(posedge clock)
    audio_reg <= clipped;

  assign io_out = audio_reg[15:0];
endmodule
