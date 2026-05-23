// Video register front-end and original/compatibility timing selector.
module VideoSys(
  input         clock,
  input         reset,
  input         io_videoClock,
  input         io_videoReset,
  input         io_prog_video_wr,
  input  [26:0] io_prog_video_addr,
  input  [15:0] io_prog_video_din,
  input         io_prog_done,
  input  [3:0]  io_options_offset_x,
  input  [3:0]  io_options_offset_y,
  input         io_options_compatibility,
  output        io_video_clockEnable,
  output        io_video_displayEnable,
  output [8:0]  io_video_pos_x,
  output [8:0]  io_video_pos_y,
  output        io_video_hSync,
  output        io_video_vSync,
  output        io_video_hBlank,
  output        io_video_vBlank,
  output [8:0]  io_video_regs_size_x,
  output [8:0]  io_video_regs_size_y,
  output [8:0]  io_video_regs_frontPorch_x,
  output [8:0]  io_video_regs_frontPorch_y,
  output [8:0]  io_video_regs_retrace_x,
  output [8:0]  io_video_regs_retrace_y,
  output        io_video_changeMode
);
  wire [2:0] videoRegAddr = io_prog_video_addr[3:1];
  wire [1:0] videoRegMask = 2'b11;
  wire [15:0] videoRegDin = {io_prog_video_din[7:0], io_prog_video_din[15:8]};

  wire [15:0] videoReg0;
  wire [15:0] videoReg1;
  wire [15:0] videoReg2;
  wire [15:0] videoReg3;
  wire [15:0] videoReg4;
  wire [15:0] videoReg5;

  wire        originalClockEnable;
  wire        originalDisplayEnable;
  wire [8:0]  originalPosX;
  wire [8:0]  originalPosY;
  wire        originalHSync;
  wire        originalVSync;
  wire        originalHBlank;
  wire        originalVBlank;

  wire        compatibilityClockEnable;
  wire        compatibilityDisplayEnable;
  wire [8:0]  compatibilityPosX;
  wire [8:0]  compatibilityPosY;
  wire        compatibilityHSync;
  wire        compatibilityVSync;
  wire        compatibilityHBlank;
  wire        compatibilityVBlank;

  reg [3:0] originalOffsetX;
  reg [3:0] originalOffsetY;
  reg [3:0] compatibilityOffsetX;
  reg [3:0] compatibilityOffsetY;
  reg       compatibilityTimingReg;

  reg        timingClockEnableReg;
  reg        timingDisplayEnableReg;
  reg [8:0]  timingPosXReg;
  reg [8:0]  timingPosYReg;
  reg        timingHSyncReg;
  reg        timingVSyncReg;
  reg        timingHBlankReg;
  reg        timingVBlankReg;

  reg [8:0] videoRegsSizeX;
  reg [8:0] videoRegsSizeY;
  reg [8:0] videoRegsFrontPorchX;
  reg [8:0] videoRegsFrontPorchY;
  reg [8:0] videoRegsRetraceX;
  reg [8:0] videoRegsRetraceY;
  reg       compatibilityChangeModeReg;

  always @(posedge io_videoClock) begin
    if (originalVSync) begin
      originalOffsetX <= io_options_offset_x;
      originalOffsetY <= io_options_offset_y;
    end

    if (compatibilityVSync) begin
      compatibilityOffsetX <= io_options_offset_x;
      compatibilityOffsetY <= io_options_offset_y;
    end

    if (originalVBlank & compatibilityVBlank)
      compatibilityTimingReg <= io_options_compatibility;

    timingClockEnableReg <= compatibilityTimingReg ? compatibilityClockEnable : originalClockEnable;
    timingDisplayEnableReg <= compatibilityTimingReg ? compatibilityDisplayEnable : originalDisplayEnable;
    timingPosXReg <= compatibilityTimingReg ? compatibilityPosX : originalPosX;
    timingPosYReg <= compatibilityTimingReg ? compatibilityPosY : originalPosY;
    timingHSyncReg <= compatibilityTimingReg ? compatibilityHSync : originalHSync;
    timingVSyncReg <= compatibilityTimingReg ? compatibilityVSync : originalVSync;
    timingHBlankReg <= compatibilityTimingReg ? compatibilityHBlank : originalHBlank;
    timingVBlankReg <= compatibilityTimingReg ? compatibilityVBlank : originalVBlank;
  end

  always @(posedge clock) begin
    if (reset) begin
      videoRegsSizeX <= 9'h140;
      videoRegsSizeY <= 9'h0F0;
      videoRegsFrontPorchX <= 9'h024;
      videoRegsFrontPorchY <= 9'h00C;
      videoRegsRetraceX <= 9'h01C;
      videoRegsRetraceY <= 9'h003;
    end
    else if (io_prog_done) begin
      videoRegsSizeX <= videoReg0[8:0];
      videoRegsSizeY <= videoReg1[8:0];
      videoRegsFrontPorchX <= videoReg2[8:0];
      videoRegsFrontPorchY <= videoReg3[8:0];
      videoRegsRetraceX <= videoReg4[8:0] + 9'h008;
      videoRegsRetraceY <= videoReg5[8:0] + 9'h001;
    end

    compatibilityChangeModeReg <= io_options_compatibility;
  end

  CaveControlRegisterFile videoRegs (
    .clock       (clock),
    .io_mem_wr   (io_prog_video_wr),
    .io_mem_addr (videoRegAddr),
    .io_mem_mask (videoRegMask),
    .io_mem_din  (videoRegDin),
    .io_regs_0   (videoReg0),
    .io_regs_1   (videoReg1),
    .io_regs_2   (videoReg2),
    .io_regs_3   (videoReg3),
    .io_regs_4   (videoReg4),
    .io_regs_5   (videoReg5)
  );

  CaveVideoTiming #(
    .H_TOTAL (10'h1C0),
    .V_TOTAL (10'h110)
  ) timing_originalVideoTiming (
    .clock                   (io_videoClock),
    .reset                   (io_videoReset),
    .io_display_x            (videoRegsSizeX),
    .io_display_y            (videoRegsSizeY),
    .io_frontPorch_x         (videoRegsFrontPorchX),
    .io_frontPorch_y         (videoRegsFrontPorchY),
    .io_retrace_x            (videoRegsRetraceX),
    .io_retrace_y            (videoRegsRetraceY),
    .io_offset_x             (originalOffsetX),
    .io_offset_y             (originalOffsetY),
    .io_timing_clockEnable   (originalClockEnable),
    .io_timing_displayEnable (originalDisplayEnable),
    .io_timing_pos_x         (originalPosX),
    .io_timing_pos_y         (originalPosY),
    .io_timing_hSync         (originalHSync),
    .io_timing_vSync         (originalVSync),
    .io_timing_hBlank        (originalHBlank),
    .io_timing_vBlank        (originalVBlank)
  );

  CaveVideoTiming #(
    .H_TOTAL (10'h1BD),
    .V_TOTAL (10'h106)
  ) timing_compatibilityVideoTiming (
    .clock                   (io_videoClock),
    .reset                   (io_videoReset),
    .io_display_x            (videoRegsSizeX),
    .io_display_y            (videoRegsSizeY),
    .io_frontPorch_x         (videoRegsFrontPorchX),
    .io_frontPorch_y         (videoRegsFrontPorchY),
    .io_retrace_x            (videoRegsRetraceX),
    .io_retrace_y            (videoRegsRetraceY),
    .io_offset_x             (compatibilityOffsetX),
    .io_offset_y             (compatibilityOffsetY),
    .io_timing_clockEnable   (compatibilityClockEnable),
    .io_timing_displayEnable (compatibilityDisplayEnable),
    .io_timing_pos_x         (compatibilityPosX),
    .io_timing_pos_y         (compatibilityPosY),
    .io_timing_hSync         (compatibilityHSync),
    .io_timing_vSync         (compatibilityVSync),
    .io_timing_hBlank        (compatibilityHBlank),
    .io_timing_vBlank        (compatibilityVBlank)
  );

  assign io_video_clockEnable = timingClockEnableReg;
  assign io_video_displayEnable = timingDisplayEnableReg;
  assign io_video_pos_x = timingPosXReg;
  assign io_video_pos_y = timingPosYReg;
  assign io_video_hSync = timingHSyncReg;
  assign io_video_vSync = timingVSyncReg;
  assign io_video_hBlank = timingHBlankReg;
  assign io_video_vBlank = timingVBlankReg;

  assign io_video_regs_size_x = videoRegsSizeX;
  assign io_video_regs_size_y = videoRegsSizeY;
  assign io_video_regs_frontPorch_x = videoRegsFrontPorchX;
  assign io_video_regs_frontPorch_y = videoRegsFrontPorchY;
  assign io_video_regs_retrace_x = videoRegsRetraceX;
  assign io_video_regs_retrace_y = videoRegsRetraceY;
  assign io_video_changeMode =
    io_prog_done | (io_options_compatibility ^ compatibilityChangeModeReg);
endmodule
