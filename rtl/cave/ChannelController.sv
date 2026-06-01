// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// YMZ280B channel scheduler and ADPCM stream state manager.
module ChannelController(
  input         clock,
  input         reset,
  input  [7:0]  io_regs_0_pitch,
  input         io_regs_0_flags_keyOn,
  input         io_regs_0_flags_loop,
  input  [7:0]  io_regs_0_level,
  input  [3:0]  io_regs_0_pan,
  input  [23:0] io_regs_0_startAddr,
  input  [23:0] io_regs_0_loopStartAddr,
  input  [23:0] io_regs_0_loopEndAddr,
  input  [23:0] io_regs_0_endAddr,
  input  [7:0]  io_regs_1_pitch,
  input         io_regs_1_flags_keyOn,
  input         io_regs_1_flags_loop,
  input  [7:0]  io_regs_1_level,
  input  [3:0]  io_regs_1_pan,
  input  [23:0] io_regs_1_startAddr,
  input  [23:0] io_regs_1_loopStartAddr,
  input  [23:0] io_regs_1_loopEndAddr,
  input  [23:0] io_regs_1_endAddr,
  input  [7:0]  io_regs_2_pitch,
  input         io_regs_2_flags_keyOn,
  input         io_regs_2_flags_loop,
  input  [7:0]  io_regs_2_level,
  input  [3:0]  io_regs_2_pan,
  input  [23:0] io_regs_2_startAddr,
  input  [23:0] io_regs_2_loopStartAddr,
  input  [23:0] io_regs_2_loopEndAddr,
  input  [23:0] io_regs_2_endAddr,
  input  [7:0]  io_regs_3_pitch,
  input         io_regs_3_flags_keyOn,
  input         io_regs_3_flags_loop,
  input  [7:0]  io_regs_3_level,
  input  [3:0]  io_regs_3_pan,
  input  [23:0] io_regs_3_startAddr,
  input  [23:0] io_regs_3_loopStartAddr,
  input  [23:0] io_regs_3_loopEndAddr,
  input  [23:0] io_regs_3_endAddr,
  input  [7:0]  io_regs_4_pitch,
  input         io_regs_4_flags_keyOn,
  input         io_regs_4_flags_loop,
  input  [7:0]  io_regs_4_level,
  input  [3:0]  io_regs_4_pan,
  input  [23:0] io_regs_4_startAddr,
  input  [23:0] io_regs_4_loopStartAddr,
  input  [23:0] io_regs_4_loopEndAddr,
  input  [23:0] io_regs_4_endAddr,
  input  [7:0]  io_regs_5_pitch,
  input         io_regs_5_flags_keyOn,
  input         io_regs_5_flags_loop,
  input  [7:0]  io_regs_5_level,
  input  [3:0]  io_regs_5_pan,
  input  [23:0] io_regs_5_startAddr,
  input  [23:0] io_regs_5_loopStartAddr,
  input  [23:0] io_regs_5_loopEndAddr,
  input  [23:0] io_regs_5_endAddr,
  input  [7:0]  io_regs_6_pitch,
  input         io_regs_6_flags_keyOn,
  input         io_regs_6_flags_loop,
  input  [7:0]  io_regs_6_level,
  input  [3:0]  io_regs_6_pan,
  input  [23:0] io_regs_6_startAddr,
  input  [23:0] io_regs_6_loopStartAddr,
  input  [23:0] io_regs_6_loopEndAddr,
  input  [23:0] io_regs_6_endAddr,
  input  [7:0]  io_regs_7_pitch,
  input         io_regs_7_flags_keyOn,
  input         io_regs_7_flags_loop,
  input  [7:0]  io_regs_7_level,
  input  [3:0]  io_regs_7_pan,
  input  [23:0] io_regs_7_startAddr,
  input  [23:0] io_regs_7_loopStartAddr,
  input  [23:0] io_regs_7_loopEndAddr,
  input  [23:0] io_regs_7_endAddr,
  input         io_enable,
  output        io_done,
  output [2:0]  io_index,
  output        io_audio_valid,
  output [15:0] io_audio_bits_left,
  output        io_rom_rd,
  output [23:0] io_rom_addr,
  input  [7:0]  io_rom_dout,
  input         io_rom_wait_n,
  input         io_rom_valid
);
  localparam [3:0] STATE_INIT    = 4'd0;
  localparam [3:0] STATE_IDLE    = 4'd1;
  localparam [3:0] STATE_READ    = 4'd2;
  localparam [3:0] STATE_LATCH   = 4'd3;
  localparam [3:0] STATE_CHECK   = 4'd4;
  localparam [3:0] STATE_READY   = 4'd5;
  localparam [3:0] STATE_PROCESS = 4'd6;
  localparam [3:0] STATE_WRITE   = 4'd7;
  localparam [3:0] STATE_NEXT    = 4'd8;
  localparam [3:0] STATE_DONE    = 4'd9;

  reg [3:0]  stateReg;
  reg [2:0]  channelCounter;
  reg [8:0]  outputCounter;
  reg [16:0] accumulatorReg_left;
  reg        pendingReg;

  reg        channelStateReg_enable;
  reg        channelStateReg_active;
  reg        channelStateReg_done;
  reg        channelStateReg_nibble;
  reg [23:0] channelStateReg_addr;
  reg        channelStateReg_loopStart;
  reg [15:0] channelStateReg_audioPipelineState_samples_0;
  reg [15:0] channelStateReg_audioPipelineState_samples_1;
  reg        channelStateReg_audioPipelineState_underflow;
  reg [15:0] channelStateReg_audioPipelineState_adpcmStep;
  reg [9:0]  channelStateReg_audioPipelineState_lerpIndex;
  reg        channelStateReg_audioPipelineState_loopEnable;
  reg [15:0] channelStateReg_audioPipelineState_loopStep;
  reg [15:0] channelStateReg_audioPipelineState_loopSample;

  reg [7:0]  channelPitch;
  reg        channelKeyOn;
  reg        channelLoop;
  reg [7:0]  channelLevel;
  reg [3:0]  channelPan;
  reg [23:0] channelStartAddr;
  reg [23:0] channelLoopStartAddr;
  reg [23:0] channelLoopEndAddr;
  reg [23:0] channelEndAddr;

  wire stateInit = stateReg == STATE_INIT;
  wire stateIdle = stateReg == STATE_IDLE;
  wire stateRead = stateReg == STATE_READ;
  wire stateLatch = stateReg == STATE_LATCH;
  wire stateCheck = stateReg == STATE_CHECK;
  wire stateReady = stateReg == STATE_READY;
  wire stateWrite = stateReg == STATE_WRITE;
  wire stateNext = stateReg == STATE_NEXT;

  wire channelCounterWrap = (stateInit | stateNext) & (&channelCounter);
  wire outputCounterWrap = outputCounter == 9'h16A;

  wire startChannel = ~channelStateReg_enable & ~channelStateReg_active & channelKeyOn;
  wire stopChannel = channelStateReg_enable & ~channelKeyOn;

  wire [3:0] audioPipeline_io_pcmData_bits =
    channelStateReg_nibble ? io_rom_dout[3:0] : io_rom_dout[7:4];

  wire        audioPipeline_io_in_ready;
  wire        audioPipeline_io_out_valid;
  wire [15:0] audioPipeline_io_out_bits_state_samples_0;
  wire [15:0] audioPipeline_io_out_bits_state_samples_1;
  wire        audioPipeline_io_out_bits_state_underflow;
  wire [15:0] audioPipeline_io_out_bits_state_adpcmStep;
  wire [9:0]  audioPipeline_io_out_bits_state_lerpIndex;
  wire        audioPipeline_io_out_bits_state_loopEnable;
  wire [15:0] audioPipeline_io_out_bits_state_loopStep;
  wire [15:0] audioPipeline_io_out_bits_state_loopSample;
  wire [16:0] audioPipeline_io_out_bits_audio_left;
  wire        audioPipeline_io_pcmData_ready;

  wire audioPipelineInputValid = stateReady;
  wire pcmDataFire = audioPipeline_io_pcmData_ready & io_rom_valid;
  wire pcmDataLowNibble = pcmDataFire & channelStateReg_nibble;
  wire atLoopEnd = channelLoop & (channelStateReg_addr == channelLoopEndAddr);
  wire atSampleEnd = channelStateReg_addr == channelEndAddr;

  wire [120:0] channelStateMemReadData;
  wire channelStateMemWriteEnable = stateInit | stateWrite;
  wire [120:0] defaultChannelState = {
    1'b0,        // enable
    1'b0,        // active
    1'b0,        // done
    1'b0,        // nibble
    24'h000000,  // addr
    1'b0,        // loopStart
    16'h0000,    // samples_1
    16'h0000,    // samples_0
    1'b1,        // underflow
    16'h007F,    // adpcmStep
    10'h000,     // lerpIndex
    1'b0,        // loopEnable
    16'h0000,    // loopStep
    16'h0000     // loopSample
  };
  wire [120:0] currentChannelState = {
    channelStateReg_enable,
    channelStateReg_active,
    channelStateReg_done,
    channelStateReg_nibble,
    channelStateReg_addr,
    channelStateReg_loopStart,
    channelStateReg_audioPipelineState_samples_1,
    channelStateReg_audioPipelineState_samples_0,
    channelStateReg_audioPipelineState_underflow,
    channelStateReg_audioPipelineState_adpcmStep,
    channelStateReg_audioPipelineState_lerpIndex,
    channelStateReg_audioPipelineState_loopEnable,
    channelStateReg_audioPipelineState_loopStep,
    channelStateReg_audioPipelineState_loopSample
  };
  wire [120:0] channelStateMemWriteData =
    stateWrite ? currentChannelState : defaultChannelState;

  wire        mem_enable = channelStateMemReadData[120];
  wire        mem_active = channelStateMemReadData[119];
  wire        mem_done = channelStateMemReadData[118];
  wire        mem_nibble = channelStateMemReadData[117];
  wire [23:0] mem_addr = channelStateMemReadData[116:93];
  wire        mem_loopStart = channelStateMemReadData[92];
  wire [15:0] mem_samples_1 = channelStateMemReadData[91:76];
  wire [15:0] mem_samples_0 = channelStateMemReadData[75:60];
  wire        mem_underflow = channelStateMemReadData[59];
  wire [15:0] mem_adpcmStep = channelStateMemReadData[58:43];
  wire [9:0]  mem_lerpIndex = channelStateMemReadData[42:33];
  wire        mem_loopEnable = channelStateMemReadData[32];
  wire [15:0] mem_loopStep = channelStateMemReadData[31:16];
  wire [15:0] mem_loopSample = channelStateMemReadData[15:0];

  wire        activeBase = stateLatch ? mem_active : channelStateReg_active;
  wire        checkedActive =
    stateCheck ? (startChannel | (~stopChannel & activeBase)) : activeBase;
  wire        nextActive = (~pcmDataLowNibble | atLoopEnd | ~atSampleEnd) & checkedActive;

  wire        doneBase = stateLatch ? mem_done : channelStateReg_done;
  wire        doneCleared =
    ~(stateCheck & (startChannel | stopChannel | channelStateReg_done)) & doneBase;
  wire        nextDone = (pcmDataLowNibble & ~atLoopEnd & atSampleEnd) | doneCleared;

  wire        nibbleBase = stateLatch ? mem_nibble : channelStateReg_nibble;
  wire        nextNibble =
    pcmDataFire ? ~channelStateReg_nibble : (~(stateCheck & startChannel) & nibbleBase);

  wire        loopStartBase = stateLatch ? mem_loopStart : channelStateReg_loopStart;
  wire        nextLoopStart =
    pcmDataFire
      ? (channelLoop & (channelStateReg_addr == channelLoopStartAddr) & ~channelStateReg_nibble)
      : (~(stateCheck & startChannel) & loopStartBase);

  wire        underflowBase = stateLatch ? mem_underflow : channelStateReg_audioPipelineState_underflow;
  wire        nextUnderflow =
    audioPipeline_io_out_valid
      ? audioPipeline_io_out_bits_state_underflow
      : ((stateCheck & startChannel) | underflowBase);

  wire        loopEnableBase = stateLatch ? mem_loopEnable : channelStateReg_audioPipelineState_loopEnable;
  wire        nextLoopEnable =
    audioPipeline_io_out_valid
      ? audioPipeline_io_out_bits_state_loopEnable
      : (~(stateCheck & startChannel) & loopEnableBase);

  wire signed [16:0] accumulatorSigned = accumulatorReg_left;
  wire signed [16:0] accumulatorLowClamped =
    (accumulatorSigned < 17'sh18000) ? 17'sh18000 : accumulatorSigned;

  always @(*) begin
    channelPitch = io_regs_7_pitch;
    channelKeyOn = io_regs_7_flags_keyOn;
    channelLoop = io_regs_7_flags_loop;
    channelLevel = io_regs_7_level;
    channelPan = io_regs_7_pan;
    channelStartAddr = io_regs_7_startAddr;
    channelLoopStartAddr = io_regs_7_loopStartAddr;
    channelLoopEndAddr = io_regs_7_loopEndAddr;
    channelEndAddr = io_regs_7_endAddr;

    case (channelCounter)
      3'd0: begin
        channelPitch = io_regs_0_pitch;
        channelKeyOn = io_regs_0_flags_keyOn;
        channelLoop = io_regs_0_flags_loop;
        channelLevel = io_regs_0_level;
        channelPan = io_regs_0_pan;
        channelStartAddr = io_regs_0_startAddr;
        channelLoopStartAddr = io_regs_0_loopStartAddr;
        channelLoopEndAddr = io_regs_0_loopEndAddr;
        channelEndAddr = io_regs_0_endAddr;
      end
      3'd1: begin
        channelPitch = io_regs_1_pitch;
        channelKeyOn = io_regs_1_flags_keyOn;
        channelLoop = io_regs_1_flags_loop;
        channelLevel = io_regs_1_level;
        channelPan = io_regs_1_pan;
        channelStartAddr = io_regs_1_startAddr;
        channelLoopStartAddr = io_regs_1_loopStartAddr;
        channelLoopEndAddr = io_regs_1_loopEndAddr;
        channelEndAddr = io_regs_1_endAddr;
      end
      3'd2: begin
        channelPitch = io_regs_2_pitch;
        channelKeyOn = io_regs_2_flags_keyOn;
        channelLoop = io_regs_2_flags_loop;
        channelLevel = io_regs_2_level;
        channelPan = io_regs_2_pan;
        channelStartAddr = io_regs_2_startAddr;
        channelLoopStartAddr = io_regs_2_loopStartAddr;
        channelLoopEndAddr = io_regs_2_loopEndAddr;
        channelEndAddr = io_regs_2_endAddr;
      end
      3'd3: begin
        channelPitch = io_regs_3_pitch;
        channelKeyOn = io_regs_3_flags_keyOn;
        channelLoop = io_regs_3_flags_loop;
        channelLevel = io_regs_3_level;
        channelPan = io_regs_3_pan;
        channelStartAddr = io_regs_3_startAddr;
        channelLoopStartAddr = io_regs_3_loopStartAddr;
        channelLoopEndAddr = io_regs_3_loopEndAddr;
        channelEndAddr = io_regs_3_endAddr;
      end
      3'd4: begin
        channelPitch = io_regs_4_pitch;
        channelKeyOn = io_regs_4_flags_keyOn;
        channelLoop = io_regs_4_flags_loop;
        channelLevel = io_regs_4_level;
        channelPan = io_regs_4_pan;
        channelStartAddr = io_regs_4_startAddr;
        channelLoopStartAddr = io_regs_4_loopStartAddr;
        channelLoopEndAddr = io_regs_4_loopEndAddr;
        channelEndAddr = io_regs_4_endAddr;
      end
      3'd5: begin
        channelPitch = io_regs_5_pitch;
        channelKeyOn = io_regs_5_flags_keyOn;
        channelLoop = io_regs_5_flags_loop;
        channelLevel = io_regs_5_level;
        channelPan = io_regs_5_pan;
        channelStartAddr = io_regs_5_startAddr;
        channelLoopStartAddr = io_regs_5_loopStartAddr;
        channelLoopEndAddr = io_regs_5_loopEndAddr;
        channelEndAddr = io_regs_5_endAddr;
      end
      3'd6: begin
        channelPitch = io_regs_6_pitch;
        channelKeyOn = io_regs_6_flags_keyOn;
        channelLoop = io_regs_6_flags_loop;
        channelLevel = io_regs_6_level;
        channelPan = io_regs_6_pan;
        channelStartAddr = io_regs_6_startAddr;
        channelLoopStartAddr = io_regs_6_loopStartAddr;
        channelLoopEndAddr = io_regs_6_loopEndAddr;
        channelEndAddr = io_regs_6_endAddr;
      end
      default: begin
      end
    endcase
  end

  always @(posedge clock) begin
    if (reset) begin
      stateReg <= STATE_INIT;
      channelCounter <= 3'd0;
      outputCounter <= 9'd0;
      pendingReg <= 1'b0;
    end
    else begin
      case (stateReg)
        STATE_INIT: begin
          if (channelCounterWrap)
            stateReg <= STATE_IDLE;
        end
        STATE_IDLE: begin
          if (io_enable)
            stateReg <= STATE_READ;
        end
        STATE_READ: begin
          stateReg <= STATE_LATCH;
        end
        STATE_LATCH: begin
          stateReg <= STATE_CHECK;
        end
        STATE_CHECK: begin
          stateReg <= (channelStateReg_active | startChannel) ? STATE_READY : STATE_WRITE;
        end
        STATE_READY: begin
          if (audioPipeline_io_in_ready)
            stateReg <= STATE_PROCESS;
        end
        STATE_PROCESS: begin
          if (audioPipeline_io_out_valid)
            stateReg <= STATE_WRITE;
        end
        STATE_WRITE: begin
          stateReg <= STATE_NEXT;
        end
        STATE_NEXT: begin
          stateReg <= channelCounterWrap ? STATE_DONE : STATE_READ;
        end
        STATE_DONE: begin
          if (outputCounterWrap)
            stateReg <= STATE_IDLE;
        end
        default: begin
          stateReg <= STATE_INIT;
        end
      endcase

      if (stateInit | stateNext)
        channelCounter <= channelCounter + 3'd1;

      outputCounter <= outputCounterWrap ? 9'd0 : outputCounter + 9'd1;
      pendingReg <= ~io_rom_valid & ((audioPipeline_io_pcmData_ready & io_rom_wait_n) | pendingReg);

      if (audioPipeline_io_out_valid) begin
        accumulatorReg_left <= accumulatorReg_left + audioPipeline_io_out_bits_audio_left;
        channelStateReg_audioPipelineState_samples_0 <= audioPipeline_io_out_bits_state_samples_0;
        channelStateReg_audioPipelineState_samples_1 <= audioPipeline_io_out_bits_state_samples_1;
        channelStateReg_audioPipelineState_adpcmStep <= audioPipeline_io_out_bits_state_adpcmStep;
        channelStateReg_audioPipelineState_lerpIndex <= audioPipeline_io_out_bits_state_lerpIndex;
        channelStateReg_audioPipelineState_loopStep <= audioPipeline_io_out_bits_state_loopStep;
        channelStateReg_audioPipelineState_loopSample <= audioPipeline_io_out_bits_state_loopSample;
      end
      else begin
        if (stateIdle)
          accumulatorReg_left <= 17'd0;

        if (stateCheck & startChannel) begin
          channelStateReg_audioPipelineState_samples_0 <= 16'd0;
          channelStateReg_audioPipelineState_samples_1 <= 16'd0;
          channelStateReg_audioPipelineState_adpcmStep <= 16'h007F;
          channelStateReg_audioPipelineState_lerpIndex <= 10'd0;
          channelStateReg_audioPipelineState_loopStep <= 16'd0;
          channelStateReg_audioPipelineState_loopSample <= 16'd0;
        end
        else if (stateLatch) begin
          channelStateReg_audioPipelineState_samples_0 <= mem_samples_0;
          channelStateReg_audioPipelineState_samples_1 <= mem_samples_1;
          channelStateReg_audioPipelineState_adpcmStep <= mem_adpcmStep;
          channelStateReg_audioPipelineState_lerpIndex <= mem_lerpIndex;
          channelStateReg_audioPipelineState_loopStep <= mem_loopStep;
          channelStateReg_audioPipelineState_loopSample <= mem_loopSample;
        end
      end

      if (stateCheck)
        channelStateReg_enable <= startChannel | (~stopChannel & channelStateReg_enable);
      else if (stateLatch)
        channelStateReg_enable <= mem_enable;

      channelStateReg_active <= nextActive;
      channelStateReg_done <= nextDone;
      channelStateReg_nibble <= nextNibble;
      channelStateReg_loopStart <= nextLoopStart;
      channelStateReg_audioPipelineState_underflow <= nextUnderflow;
      channelStateReg_audioPipelineState_loopEnable <= nextLoopEnable;

      if (pcmDataLowNibble) begin
        if (atLoopEnd)
          channelStateReg_addr <= channelLoopStartAddr;
        else if (atSampleEnd) begin
          if (stateCheck & startChannel)
            channelStateReg_addr <= channelStartAddr;
          else if (stateLatch)
            channelStateReg_addr <= mem_addr;
        end
        else
          channelStateReg_addr <= channelStateReg_addr + 24'd1;
      end
      else if (stateCheck & startChannel)
        channelStateReg_addr <= channelStartAddr;
      else if (stateLatch)
        channelStateReg_addr <= mem_addr;
    end
  end

  CaveSyncReadMem #(
    .ADDR_WIDTH (3),
    .DATA_WIDTH (121),
    .DEPTH      (8)
  ) channelStateMem_ext (
    .read_addr  (channelCounter),
    .read_en    (stateRead),
    .read_clk   (clock),
    .read_data  (channelStateMemReadData),
    .write_addr (channelCounter),
    .write_en   (channelStateMemWriteEnable),
    .write_clk  (clock),
    .write_data (channelStateMemWriteData)
  );

  AudioPipeline audioPipeline (
    .clock                        (clock),
    .reset                        (reset),
    .io_in_ready                  (audioPipeline_io_in_ready),
    .io_in_valid                  (audioPipelineInputValid),
    .io_in_bits_state_samples_0   (channelStateReg_audioPipelineState_samples_0),
    .io_in_bits_state_samples_1   (channelStateReg_audioPipelineState_samples_1),
    .io_in_bits_state_underflow   (channelStateReg_audioPipelineState_underflow),
    .io_in_bits_state_adpcmStep   (channelStateReg_audioPipelineState_adpcmStep),
    .io_in_bits_state_lerpIndex   (channelStateReg_audioPipelineState_lerpIndex),
    .io_in_bits_state_loopEnable  (channelStateReg_audioPipelineState_loopEnable),
    .io_in_bits_state_loopStep    (channelStateReg_audioPipelineState_loopStep),
    .io_in_bits_state_loopSample  (channelStateReg_audioPipelineState_loopSample),
    .io_in_bits_pitch             (channelPitch),
    .io_in_bits_level             (channelLevel),
    .io_in_bits_pan               (channelPan),
    .io_out_valid                 (audioPipeline_io_out_valid),
    .io_out_bits_state_samples_0  (audioPipeline_io_out_bits_state_samples_0),
    .io_out_bits_state_samples_1  (audioPipeline_io_out_bits_state_samples_1),
    .io_out_bits_state_underflow  (audioPipeline_io_out_bits_state_underflow),
    .io_out_bits_state_adpcmStep  (audioPipeline_io_out_bits_state_adpcmStep),
    .io_out_bits_state_lerpIndex  (audioPipeline_io_out_bits_state_lerpIndex),
    .io_out_bits_state_loopEnable (audioPipeline_io_out_bits_state_loopEnable),
    .io_out_bits_state_loopStep   (audioPipeline_io_out_bits_state_loopStep),
    .io_out_bits_state_loopSample (audioPipeline_io_out_bits_state_loopSample),
    .io_out_bits_audio_left       (audioPipeline_io_out_bits_audio_left),
    .io_pcmData_ready             (audioPipeline_io_pcmData_ready),
    .io_pcmData_valid             (io_rom_valid),
    .io_pcmData_bits              (audioPipeline_io_pcmData_bits),
    .io_loopStart                 (channelStateReg_loopStart)
  );

  assign io_done = stateCheck & channelStateReg_done;
  assign io_index = channelCounter;
  assign io_audio_valid = outputCounterWrap;
  assign io_audio_bits_left =
    (accumulatorLowClamped < 17'sh07FFF) ? accumulatorLowClamped[15:0] : 16'h7FFF;
  assign io_rom_rd = audioPipeline_io_pcmData_ready & ~pendingReg;
  assign io_rom_addr = channelStateReg_addr;
endmodule
