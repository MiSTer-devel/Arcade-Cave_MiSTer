module Crossing(
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

  DualClockFIFO addrFifo (
    .clock        (io_targetClock),
    .io_readClock (clock),
    .io_deq_ready (io_out_wait_n),
    .io_deq_valid (io_out_rd),
    .io_deq_bits  (io_out_addr),
    .io_enq_valid (io_in_rd),
    .io_enq_bits  (io_in_addr)
  );
  DualClockFIFO_1 dataFifo (
    .clock        (clock),
    .io_readClock (io_targetClock),
    .io_deq_bits  (io_in_dout),
    .io_enq_valid (io_out_valid),
    .io_enq_bits  (io_out_dout)
  );
endmodule

