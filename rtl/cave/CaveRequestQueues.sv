// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Framebuffer write request queues.

// Queues 16-bit framebuffer writes and coalesces adjacent pixels onto the 64-bit DDR write bus.
module CaveSpriteFramebufferRequestQueue (
  input         clock,
  input         io_enable,
  input         io_readClock,
  input         io_in_wr,
  input  [16:0] io_in_addr,
  input  [15:0] io_in_din,
  output        io_in_wait_n,
  output        io_out_wr,
  output [31:0] io_out_addr,
  output [7:0]  io_out_mask,
  output [63:0] io_out_din,
  input         io_out_wait_n
);
  reg         pendingValid;
  reg  [14:0] pendingAddr;
  reg  [7:0]  pendingMask;
  reg  [63:0] pendingDin;

  function [7:0] expand_mask;
    input [1:0] lane;
    begin
      case (lane)
        2'h0: expand_mask = 8'b00000011;
        2'h1: expand_mask = 8'b00001100;
        2'h2: expand_mask = 8'b00110000;
        2'h3: expand_mask = 8'b11000000;
        default: expand_mask = 8'b00000011;
      endcase
    end
  endfunction

  function [63:0] expand_bit_mask;
    input [1:0] lane;
    begin
      case (lane)
        2'h0: expand_bit_mask = 64'h0000_0000_0000_ffff;
        2'h1: expand_bit_mask = 64'h0000_0000_ffff_0000;
        2'h2: expand_bit_mask = 64'h0000_ffff_0000_0000;
        2'h3: expand_bit_mask = 64'hffff_0000_0000_0000;
        default: expand_bit_mask = 64'h0000_0000_0000_ffff;
      endcase
    end
  endfunction

  function [63:0] expand_data;
    input [1:0]  lane;
    input [15:0] din;
    begin
      case (lane)
        2'h0: expand_data = {48'h0, din};
        2'h1: expand_data = {32'h0, din, 16'h0};
        2'h2: expand_data = {16'h0, din, 32'h0};
        2'h3: expand_data = {din, 48'h0};
        default: expand_data = {48'h0, din};
      endcase
    end
  endfunction

  wire        fifo_enq_ready;
  wire        fifo_deq_valid;
  wire [86:0] fifo_deq_bits;
  wire [86:0] fifo_enq_bits = {pendingAddr, pendingDin, pendingMask};
  wire [14:0] fifo_deq_addr = fifo_deq_bits[86:72];
  wire [63:0] fifo_deq_din = fifo_deq_bits[71:8];
  wire [7:0]  fifo_deq_mask = fifo_deq_bits[7:0];

  wire [14:0] inWordAddr = io_in_addr[16:2];
  wire [7:0]  inByteMask = expand_mask(io_in_addr[1:0]);
  wire [63:0] inBitMask = expand_bit_mask(io_in_addr[1:0]);
  wire [63:0] inDin = expand_data(io_in_addr[1:0], io_in_din);
  wire        samePending = pendingValid & (pendingAddr == inWordAddr);
  wire        flushPending = pendingValid & (~io_in_wr | ~samePending);
  wire        acceptInput =
    io_in_wr & (samePending | ~pendingValid | fifo_enq_ready);

  always @(posedge clock) begin
    if (~io_enable) begin
      pendingValid <= 1'b0;
      pendingAddr <= 15'd0;
      pendingMask <= 8'd0;
      pendingDin <= 64'd0;
    end
    else if (flushPending & fifo_enq_ready) begin
      if (io_in_wr) begin
        pendingValid <= 1'b1;
        pendingAddr <= inWordAddr;
        pendingMask <= inByteMask;
        pendingDin <= inDin;
      end
      else begin
        pendingValid <= 1'b0;
      end
    end
    else if (acceptInput) begin
      if (samePending) begin
        pendingMask <= pendingMask | inByteMask;
        pendingDin <= (pendingDin & ~inBitMask) | (inDin & inBitMask);
      end
      else begin
        pendingValid <= 1'b1;
        pendingAddr <= inWordAddr;
        pendingMask <= inByteMask;
        pendingDin <= inDin;
      end
    end
  end

  CaveDualClockFIFO #(
    .DATA_WIDTH (87),
    .DEPTH      (16)
  ) fifo (
    .write_clock (clock),
    .read_clock  (io_readClock),
    .deq_ready   (io_out_wait_n),
    .deq_valid   (fifo_deq_valid),
    .deq_bits    (fifo_deq_bits),
    .enq_ready   (fifo_enq_ready),
    .enq_valid   (flushPending),
    .enq_bits    (fifo_enq_bits)
  );

  assign io_in_wait_n = samePending | ~pendingValid | fifo_enq_ready;
  assign io_out_wr = io_enable & fifo_deq_valid;
  assign io_out_addr = {14'b0, fifo_deq_addr, 3'b000};
  assign io_out_mask = fifo_deq_mask;
  assign io_out_din = fifo_deq_din;
endmodule

// Queues 32-bit framebuffer writes and expands them onto the 64-bit DDR write bus.
module CaveSystemFramebufferRequestQueue (
  input         clock,
  input         io_enable,
  input         io_readClock,
  input         io_in_wr,
  input  [16:0] io_in_addr,
  input  [31:0] io_in_din,
  output        io_out_wr,
  output [31:0] io_out_addr,
  output [7:0]  io_out_mask,
  output [63:0] io_out_din,
  input         io_out_wait_n
);
  wire        fifo_deq_valid;
  wire [53:0] fifo_deq_bits;
  wire [53:0] fifo_enq_bits = {io_in_wr, io_in_addr, io_in_din, 4'hF};
  wire        fifo_enq_ready_unused;
  wire [16:0] fifo_deq_addr = fifo_deq_bits[52:36];
  wire [31:0] fifo_deq_din = fifo_deq_bits[35:4];
  wire [3:0]  fifo_deq_mask = fifo_deq_bits[3:0];

  CaveDualClockFIFO #(
    .DATA_WIDTH (54),
    .DEPTH      (16)
  ) fifo (
    .write_clock (clock),
    .read_clock  (io_readClock),
    .deq_ready   (io_out_wait_n),
    .deq_valid   (fifo_deq_valid),
    .deq_bits    (fifo_deq_bits),
    .enq_ready   (fifo_enq_ready_unused),
    .enq_valid   (io_in_wr),
    .enq_bits    (fifo_enq_bits)
  );

  assign io_out_wr = io_enable & fifo_deq_valid;
  assign io_out_addr = {13'b0, fifo_deq_addr, 2'b00};
  assign io_out_mask = fifo_deq_addr[0] ? {fifo_deq_mask, 4'b0000} : {4'b0000, fifo_deq_mask};
  assign io_out_din = {2{fifo_deq_din}};
endmodule
