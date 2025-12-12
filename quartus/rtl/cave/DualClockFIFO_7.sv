module DualClockFIFO_7(
  input         clock,
  input         io_readClock,
  input         io_deq_ready,
  output        io_deq_valid,
  output [16:0] io_deq_bits_addr,
  output [31:0] io_deq_bits_din,
  output [3:0]  io_deq_bits_mask,
  input         io_enq_valid,
  input         io_enq_bits_wr,
  input  [16:0] io_enq_bits_addr,
  input  [31:0] io_enq_bits_din
);

  wire        io_enq_ready;
  wire [53:0] _fifo_data;
  wire        _fifo_rdreq;
  wire        _fifo_wrreq;
  wire [53:0] _fifo_q;
  wire        _fifo_rdempty;
  wire        _fifo_wrfull;
  assign io_enq_ready = ~_fifo_wrfull;
  assign _fifo_data = {io_enq_bits_wr, io_enq_bits_addr, io_enq_bits_din, 4'hF};
  assign _fifo_rdreq = io_deq_ready & ~_fifo_rdempty;
  assign _fifo_wrreq = io_enq_ready & io_enq_valid;
  dual_clock_fifo #(
    .DATA_WIDTH(54),
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
  assign io_deq_bits_addr = _fifo_q[52:36];
  assign io_deq_bits_din = _fifo_q[35:4];
  assign io_deq_bits_mask = _fifo_q[3:0];
endmodule

