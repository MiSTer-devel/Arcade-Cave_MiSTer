// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// YMZ280B per-channel sample processing pipeline.
module AudioPipeline(
  input         clock,
  input         reset,
  output        io_in_ready,
  input         io_in_valid,
  input  [15:0] io_in_bits_state_samples_0,
  input  [15:0] io_in_bits_state_samples_1,
  input         io_in_bits_state_underflow,
  input  [15:0] io_in_bits_state_adpcmStep,
  input  [9:0]  io_in_bits_state_lerpIndex,
  input         io_in_bits_state_loopEnable,
  input  [15:0] io_in_bits_state_loopStep,
  input  [15:0] io_in_bits_state_loopSample,
  input  [7:0]  io_in_bits_pitch,
  input  [7:0]  io_in_bits_level,
  input  [3:0]  io_in_bits_pan,
  output        io_out_valid,
  output [15:0] io_out_bits_state_samples_0,
  output [15:0] io_out_bits_state_samples_1,
  output        io_out_bits_state_underflow,
  output [15:0] io_out_bits_state_adpcmStep,
  output [9:0]  io_out_bits_state_lerpIndex,
  output        io_out_bits_state_loopEnable,
  output [15:0] io_out_bits_state_loopStep,
  output [15:0] io_out_bits_state_loopSample,
  output [16:0] io_out_bits_audio_left,
  output        io_pcmData_ready,
  input         io_pcmData_valid,
  input  [3:0]  io_pcmData_bits,
  input         io_loopStart
);
  localparam [2:0] STATE_IDLE        = 3'd0;
  localparam [2:0] STATE_CHECK       = 3'd1;
  localparam [2:0] STATE_FETCH       = 3'd2;
  localparam [2:0] STATE_DECODE      = 3'd3;
  localparam [2:0] STATE_INTERPOLATE = 3'd4;
  localparam [2:0] STATE_LEVEL       = 3'd5;
  localparam [2:0] STATE_PAN         = 3'd6;
  localparam [2:0] STATE_DONE        = 3'd7;

  reg  [2:0]  stateReg;
  reg  [15:0] inputReg_state_samples_0;
  reg  [15:0] inputReg_state_samples_1;
  reg         inputReg_state_underflow;
  reg  [15:0] inputReg_state_adpcmStep;
  reg  [9:0]  inputReg_state_lerpIndex;
  reg         inputReg_state_loopEnable;
  reg  [15:0] inputReg_state_loopStep;
  reg  [15:0] inputReg_state_loopSample;
  reg  [7:0]  inputReg_pitch;
  reg  [7:0]  inputReg_level;
  reg  [3:0]  inputReg_pan;
  reg  [16:0] sampleReg;
  reg  [16:0] audioReg_left;
  reg  [3:0]  pcmDataReg;

  wire        idle = stateReg == STATE_IDLE;
  wire        fetch = stateReg == STATE_FETCH;
  wire        decode = stateReg == STATE_DECODE;
  wire        interpolate = stateReg == STATE_INTERPOLATE;
  wire        level = stateReg == STATE_LEVEL;
  wire        pan = stateReg == STATE_PAN;

  wire        inputFire = idle & io_in_valid;
  wire        pcmDataFire = fetch & io_pcmData_valid;
  wire        replayLoopSample = io_loopStart & inputReg_state_loopEnable;
  wire        captureLoopSample = decode & io_loopStart & ~inputReg_state_loopEnable;

  wire [16:0] adpcmInStep = {inputReg_state_adpcmStep[15], inputReg_state_adpcmStep};
  wire [16:0] sample0 = {inputReg_state_samples_0[15], inputReg_state_samples_0};
  wire [16:0] sample1 = {inputReg_state_samples_1[15], inputReg_state_samples_1};
  wire [16:0] adpcmOutStep;
  wire [16:0] adpcmOutSample;
  wire [16:0] lerpOutSample;

  wire [8:0]  pitchStep = {1'b0, inputReg_pitch} + 9'd1;
  wire [9:0]  nextLerpIndex = inputReg_state_lerpIndex + {1'b0, pitchStep};

  wire signed [25:0] levelSample = {{9{sampleReg[16]}}, sampleReg};
  wire signed [25:0] levelFactor = {17'd0, {1'b0, inputReg_level} + 9'd1};
  wire signed [25:0] levelProduct = levelSample * levelFactor;

  wire signed [19:0] panSample = {{3{sampleReg[16]}}, sampleReg};
  wire signed [19:0] panFactor = {17'd0, ~inputReg_pan[2:0]};
  wire signed [19:0] panProduct = panSample * panFactor;

  reg  [2:0]  nextState;
  always @(*) begin
    case (stateReg)
      STATE_IDLE:
        nextState = io_in_valid ? STATE_CHECK : STATE_IDLE;
      STATE_CHECK:
        nextState = inputReg_state_underflow ? STATE_FETCH : STATE_INTERPOLATE;
      STATE_FETCH:
        nextState = io_pcmData_valid ? STATE_DECODE : STATE_FETCH;
      STATE_DECODE:
        nextState = STATE_INTERPOLATE;
      STATE_INTERPOLATE:
        nextState = STATE_LEVEL;
      STATE_LEVEL:
        nextState = STATE_PAN;
      STATE_PAN:
        nextState = STATE_DONE;
      default:
        nextState = STATE_IDLE;
    endcase
  end

  always @(posedge clock) begin
    if (reset)
      stateReg <= STATE_IDLE;
    else
      stateReg <= nextState;

    if (decode) begin
      inputReg_state_samples_0 <= inputReg_state_samples_1;
      inputReg_state_samples_1 <=
        replayLoopSample ? inputReg_state_loopSample : adpcmOutSample[15:0];
      inputReg_state_adpcmStep <=
        replayLoopSample ? inputReg_state_loopStep : adpcmOutStep[15:0];
    end
    else if (inputFire) begin
      inputReg_state_samples_0 <= io_in_bits_state_samples_0;
      inputReg_state_samples_1 <= io_in_bits_state_samples_1;
      inputReg_state_adpcmStep <= io_in_bits_state_adpcmStep;
    end

    if (interpolate) begin
      inputReg_state_underflow <= nextLerpIndex[9];
      inputReg_state_lerpIndex <= {1'b0, nextLerpIndex[8:0]};
    end
    else if (inputFire) begin
      inputReg_state_underflow <= io_in_bits_state_underflow;
      inputReg_state_lerpIndex <= io_in_bits_state_lerpIndex;
    end

    if (captureLoopSample) begin
      inputReg_state_loopEnable <= 1'b1;
      inputReg_state_loopStep <= adpcmOutStep[15:0];
      inputReg_state_loopSample <= adpcmOutSample[15:0];
    end
    else if (inputFire) begin
      inputReg_state_loopEnable <= io_in_bits_state_loopEnable;
      inputReg_state_loopStep <= io_in_bits_state_loopStep;
      inputReg_state_loopSample <= io_in_bits_state_loopSample;
    end

    if (inputFire) begin
      inputReg_pitch <= io_in_bits_pitch;
      inputReg_level <= io_in_bits_level;
      inputReg_pan <= io_in_bits_pan;
    end

    if (level)
      sampleReg <= levelProduct[25:9];
    else if (interpolate)
      sampleReg <= lerpOutSample;

    if (pan)
      audioReg_left <= inputReg_pan > 4'd8 ? panProduct[19:3] : sampleReg;

    if (pcmDataFire)
      pcmDataReg <= io_pcmData_bits;
  end

  CaveYmzAdpcmDecoder adpcm (
    .io_data       (pcmDataReg),
    .io_in_step    (adpcmInStep),
    .io_in_sample  (sample1),
    .io_out_step   (adpcmOutStep),
    .io_out_sample (adpcmOutSample)
  );

  CaveYmzLinearInterpolator lerp (
    .io_samples_0 (sample0),
    .io_samples_1 (sample1),
    .io_index     (inputReg_state_lerpIndex),
    .io_out       (lerpOutSample)
  );

  assign io_in_ready = idle;
  assign io_out_valid = stateReg == STATE_DONE;
  assign io_out_bits_state_samples_0 = inputReg_state_samples_0;
  assign io_out_bits_state_samples_1 = inputReg_state_samples_1;
  assign io_out_bits_state_underflow = inputReg_state_underflow;
  assign io_out_bits_state_adpcmStep = inputReg_state_adpcmStep;
  assign io_out_bits_state_lerpIndex = inputReg_state_lerpIndex;
  assign io_out_bits_state_loopEnable = inputReg_state_loopEnable;
  assign io_out_bits_state_loopStep = inputReg_state_loopStep;
  assign io_out_bits_state_loopSample = inputReg_state_loopSample;
  assign io_out_bits_audio_left = audioReg_left;
  assign io_pcmData_ready = fetch;
endmodule
