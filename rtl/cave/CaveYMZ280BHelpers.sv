// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Yamaha YMZ280B ADPCM nibble decoder.
module CaveYmzAdpcmDecoder(
  input  [3:0]  io_data,
  input  [16:0] io_in_step,
  input  [16:0] io_in_sample,
  output [16:0] io_out_step,
  output [16:0] io_out_sample
);
  localparam signed [19:0] STEP_MIN   = 20'sd127;
  localparam signed [19:0] STEP_MAX   = 20'sd24576;
  localparam signed [19:0] SAMPLE_MIN = -20'sd32768;
  localparam signed [19:0] SAMPLE_MAX = 20'sd32767;

  reg signed [10:0] stepScale;
  always @(*) begin
    case (io_data[2:0])
      3'd0,
      3'd1,
      3'd2,
      3'd3: stepScale = 11'sd230;
      3'd4: stepScale = 11'sd307;
      3'd5: stepScale = 11'sd409;
      3'd6: stepScale = 11'sd512;
      default: stepScale = 11'sd614;
    endcase
  end

  reg signed [4:0] deltaScale;
  always @(*) begin
    case (io_data)
      4'h0: deltaScale = 5'sd1;
      4'h1: deltaScale = 5'sd3;
      4'h2: deltaScale = 5'sd5;
      4'h3: deltaScale = 5'sd7;
      4'h4: deltaScale = 5'sd9;
      4'h5: deltaScale = 5'sd11;
      4'h6: deltaScale = 5'sd13;
      4'h7: deltaScale = 5'sd15;
      4'h8: deltaScale = -5'sd1;
      4'h9: deltaScale = -5'sd3;
      4'hA: deltaScale = -5'sd5;
      4'hB: deltaScale = -5'sd7;
      4'hC: deltaScale = -5'sd9;
      4'hD: deltaScale = -5'sd11;
      4'hE: deltaScale = -5'sd13;
      default: deltaScale = -5'sd15;
    endcase
  end

  wire signed [27:0] stepProduct =
    $signed({{11{io_in_step[16]}}, io_in_step}) *
    $signed({{17{stepScale[10]}}, stepScale});
  wire signed [19:0] scaledStep = stepProduct[27:8];
  wire signed [19:0] stepMinClamped = scaledStep < STEP_MIN ? STEP_MIN : scaledStep;
  wire signed [16:0] nextStep = stepMinClamped < STEP_MAX ? stepMinClamped[16:0] : STEP_MAX[16:0];

  wire signed [21:0] deltaProduct =
    $signed({{5{io_in_step[16]}}, io_in_step}) *
    $signed({{17{deltaScale[4]}}, deltaScale});
  wire signed [19:0] delta = {deltaProduct[21], deltaProduct[21:3]};
  wire signed [19:0] sample = {{3{io_in_sample[16]}}, io_in_sample};
  wire signed [19:0] sampleSum = sample + delta;
  wire signed [19:0] sampleMinClamped = sampleSum < SAMPLE_MIN ? SAMPLE_MIN : sampleSum;
  wire signed [16:0] nextSample =
    sampleMinClamped < SAMPLE_MAX ? sampleMinClamped[16:0] : SAMPLE_MAX[16:0];

  assign io_out_step = nextStep;
  assign io_out_sample = nextSample;
endmodule

// Linear interpolation helper for YMZ280B sample playback.
module CaveYmzLinearInterpolator (
  input  [16:0] io_samples_0,
  input  [16:0] io_samples_1,
  input  [9:0]  io_index,
  output [16:0] io_out
);
  wire signed [16:0] sample_0 = io_samples_0;
  wire signed [16:0] sample_1 = io_samples_1;
  wire signed [17:0] slope = {sample_1[16], sample_1} - {sample_0[16], sample_0};
  wire signed [25:0] slope_ext = {{8{slope[17]}}, slope};
  wire signed [25:0] scaled_offset = slope_ext * $signed({16'h0000, io_index});
  wire signed [16:0] interpolated = scaled_offset[25:9] + sample_0;

  assign io_out = interpolated;
endmodule
