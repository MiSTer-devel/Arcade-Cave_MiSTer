module DualClockFIFO_1(
  input         clock,
  input         io_readClock,
  output [63:0] io_deq_bits,
  input         io_enq_valid,
  input  [63:0] io_enq_bits
);

  wire io_enq_ready;
  wire _fifo_rdreq;
  wire _fifo_wrreq;
  wire _fifo_rdempty;
  wire _fifo_wrfull;
  assign io_enq_ready = ~_fifo_wrfull;
  assign _fifo_rdreq = ~_fifo_rdempty;
  assign _fifo_wrreq = io_enq_ready & io_enq_valid;
  dual_clock_fifo #(
    .DATA_WIDTH(64),
    .DEPTH(4)
  ) fifo (
    .data    (io_enq_bits),
    .rdclk   (io_readClock),
    .rdreq   (_fifo_rdreq),
    .wrclk   (clock),
    .wrreq   (_fifo_wrreq),
    .q       (io_deq_bits),
    .rdempty (_fifo_rdempty),
    .wrfull  (_fifo_wrfull)
  );
endmodule

