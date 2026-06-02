// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module SystemFrameBuffer(
  input         clock,
  input         reset,
  input         io_videoClock,
  input         io_enable,
  input         io_rotate,
  input         io_forceBlank,
  input         io_video_vBlank,
  input  [8:0]  io_video_regs_size_x,
  input  [8:0]  io_video_regs_size_y,
  output        io_frameBufferCtrl_enable,
  output [11:0] io_frameBufferCtrl_hSize,
  output [11:0] io_frameBufferCtrl_vSize,
  output [31:0] io_frameBufferCtrl_baseAddr,
  output [13:0] io_frameBufferCtrl_stride,
  input         io_frameBufferCtrl_vBlank,
  input         io_frameBufferCtrl_lowLat,
  output        io_frameBufferCtrl_forceBlank,
  input         io_frameBuffer_wr,
  input  [16:0] io_frameBuffer_addr,
  input  [31:0] io_frameBuffer_din,
  output        io_ddr_wr,
  output [31:0] io_ddr_addr,
  output [7:0]  io_ddr_mask,
  output [63:0] io_ddr_din,
  input         io_ddr_wait_n
);
  wire [8:0]  frameWidth = io_rotate ? io_video_regs_size_y : io_video_regs_size_x;
  wire [8:0]  frameHeight = io_rotate ? io_video_regs_size_x : io_video_regs_size_y;

  wire        pageMode = ~io_frameBufferCtrl_lowLat;
  wire        pageSwapRead;
  wire        pageSwapWrite;
  wire [31:0] pageAddrWrite;

  wire [31:0] queueAddr;

  reg frameCtrlVBlank_r;
  reg frameCtrlVBlank;
  reg frameCtrlVBlankPrev;
  reg videoVBlank_r;
  reg videoVBlank;
  reg videoVBlankPrev;

  always @(posedge clock) begin
    frameCtrlVBlank_r <= io_frameBufferCtrl_vBlank;
    frameCtrlVBlank <= frameCtrlVBlank_r;
    frameCtrlVBlankPrev <= frameCtrlVBlank;
    videoVBlank_r <= io_video_vBlank;
    videoVBlank <= videoVBlank_r;
    videoVBlankPrev <= videoVBlank;
  end

  assign pageSwapRead = frameCtrlVBlank & ~frameCtrlVBlankPrev;
  assign pageSwapWrite = videoVBlank & ~videoVBlankPrev;

  CavePageFlipper #(
    .BASE_PAGE             (11'h120),
    .SUPPORT_TRIPLE_BUFFER (1'b1)
  ) pageFlipper (
    .clock        (clock),
    .reset        (reset),
    .io_mode      (pageMode),
    .io_swapRead  (pageSwapRead),
    .io_swapWrite (pageSwapWrite),
    .io_addrRead  (io_frameBufferCtrl_baseAddr),
    .io_addrWrite (pageAddrWrite)
  );

  CaveSystemFramebufferRequestQueue queue (
    .clock         (io_videoClock),
    .io_enable     (io_enable),
    .io_readClock  (clock),
    .io_in_wr      (io_frameBuffer_wr),
    .io_in_addr    (io_frameBuffer_addr),
    .io_in_din     (io_frameBuffer_din),
    .io_out_wr     (io_ddr_wr),
    .io_out_addr   (queueAddr),
    .io_out_mask   (io_ddr_mask),
    .io_out_din    (io_ddr_din),
    .io_out_wait_n (io_ddr_wait_n)
  );

  assign io_frameBufferCtrl_enable = io_enable;
  assign io_frameBufferCtrl_hSize = {3'h0, frameWidth};
  assign io_frameBufferCtrl_vSize = {3'h0, frameHeight};
  assign io_frameBufferCtrl_stride = {3'h0, frameWidth, 2'h0};
  assign io_frameBufferCtrl_forceBlank = io_forceBlank;
  assign io_ddr_addr = 32'(queueAddr + pageAddrWrite);
endmodule
