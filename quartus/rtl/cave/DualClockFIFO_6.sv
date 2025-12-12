module DualClockFIFO_6(
  input         clock,
  input         io_readClock,
  input         io_deq_ready,
  output        io_deq_valid,
  output [16:0] io_deq_bits_addr,
  output [15:0] io_deq_bits_din,
  output [1:0]  io_deq_bits_mask,
  output        io_enq_ready,
  input         io_enq_valid,
  input         io_enq_bits_wr,
  input  [16:0] io_enq_bits_addr,
  input  [15:0] io_enq_bits_din
);

  wire [35:0] _fifo_data;
  wire        _fifo_rdreq;
  wire        _fifo_wrreq;
  wire [35:0] _fifo_q;
  wire        _fifo_rdempty;
  wire        _fifo_wrfull;
  assign _fifo_data = {io_enq_bits_wr, io_enq_bits_addr, io_enq_bits_din, 2'h3};
  assign _fifo_rdreq = io_deq_ready & ~_fifo_rdempty;
  assign _fifo_wrreq = ~_fifo_wrfull & io_enq_valid;
  dual_clock_fifo #(
    .DATA_WIDTH(36),
    .DEPTH(16)
  ) fifo (
    .data    (_fifo_data),
    .rdclk   (io_readClock),
    .rdreq   (_fifo_rdreq),
    .wrclk   (clock),
    .wrreq   (_fifo_wrreq),
    .q       (_fifo_q),
    .rdempty (_fifo_rdempty),
    .wrfull  (_fifo_wrfull)
  );
  assign io_deq_valid = ~_fifo_rdempty;
  assign io_deq_bits_addr = _fifo_q[34:18];
  assign io_deq_bits_din = _fifo_q[17:2];
  assign io_deq_bits_mask = _fifo_q[1:0];
  assign io_enq_ready = ~_fifo_wrfull;
endmodule

