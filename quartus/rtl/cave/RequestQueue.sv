module RequestQueue(
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

  wire        _fifo_io_deq_valid;
  wire [16:0] _fifo_io_deq_bits_addr;
  wire [15:0] _fifo_io_deq_bits_din;
  wire [1:0]  _fifo_io_deq_bits_mask;
  wire [3:0]  _io_out_mask_T_5 =
    _fifo_io_deq_bits_addr[1:0] == 2'h1
      ? {_fifo_io_deq_bits_mask, 2'h0}
      : {2'h0, _fifo_io_deq_bits_mask};
  wire [5:0]  _io_out_mask_T_7 =
    _fifo_io_deq_bits_addr[1:0] == 2'h2
      ? {_fifo_io_deq_bits_mask, 4'h0}
      : {2'h0, _io_out_mask_T_5};
  DualClockFIFO_6 fifo (
    .clock            (clock),
    .io_readClock     (io_readClock),
    .io_deq_ready     (io_out_wait_n),
    .io_deq_valid     (_fifo_io_deq_valid),
    .io_deq_bits_addr (_fifo_io_deq_bits_addr),
    .io_deq_bits_din  (_fifo_io_deq_bits_din),
    .io_deq_bits_mask (_fifo_io_deq_bits_mask),
    .io_enq_ready     (io_in_wait_n),
    .io_enq_valid     (io_in_wr),
    .io_enq_bits_wr   (io_in_wr),
    .io_enq_bits_addr (io_in_addr),
    .io_enq_bits_din  (io_in_din)
  );
  assign io_out_wr = io_enable & _fifo_io_deq_valid;
  assign io_out_addr = {14'h0, _fifo_io_deq_bits_addr, 1'h0};
  assign io_out_mask =
    (&(_fifo_io_deq_bits_addr[1:0]))
      ? {_fifo_io_deq_bits_mask, 6'h0}
      : {2'h0, _io_out_mask_T_7};
  assign io_out_din = {4{_fifo_io_deq_bits_din}};
endmodule

