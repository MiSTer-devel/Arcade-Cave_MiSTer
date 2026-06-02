// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module CaveTileRomClockCrossing(
  input         clock,
  input         io_targetClock,
  input         io_in_rd,
  input  [31:0] io_in_addr,
  output [63:0] io_in_dout,
  output        io_out_rd,
  output [31:0] io_out_addr,
  input  [63:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid
);
  wire addr_fifo_enq_ready_unused;
  wire data_fifo_enq_ready_unused;
  wire data_fifo_deq_valid_unused;

  CaveDualClockFIFO #(
    .DATA_WIDTH (32),
    .DEPTH      (4)
  ) addr_fifo (
    .write_clock (io_targetClock),
    .read_clock  (clock),
    .deq_ready   (io_out_wait_n),
    .deq_valid   (io_out_rd),
    .deq_bits    (io_out_addr),
    .enq_ready   (addr_fifo_enq_ready_unused),
    .enq_valid   (io_in_rd),
    .enq_bits    (io_in_addr)
  );

  CaveDualClockFIFO #(
    .DATA_WIDTH (64),
    .DEPTH      (4)
  ) data_fifo (
    .write_clock (clock),
    .read_clock  (io_targetClock),
    .deq_ready   (1'b1),
    .deq_valid   (data_fifo_deq_valid_unused),
    .deq_bits    (io_in_dout),
    .enq_ready   (data_fifo_enq_ready_unused),
    .enq_valid   (io_out_valid),
    .enq_bits    (io_out_dout)
  );
endmodule
