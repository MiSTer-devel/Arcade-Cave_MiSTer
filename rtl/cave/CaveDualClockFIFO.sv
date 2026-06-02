// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Shared dual-clock FIFO wrapper around the existing Arcadia VHDL FIFO block.
module CaveDualClockFIFO #(
  parameter DATA_WIDTH = 8,
  parameter DEPTH = 4
) (
  input                   write_clock,
  input                   read_clock,
  input                   deq_ready,
  output                  deq_valid,
  output [DATA_WIDTH-1:0] deq_bits,
  output                  enq_ready,
  input                   enq_valid,
  input  [DATA_WIDTH-1:0] enq_bits
);
  wire read_empty;
  wire write_full;

  wire read_request = deq_ready & ~read_empty;
  wire write_request = ~write_full & enq_valid;

  dual_clock_fifo #(
    .DATA_WIDTH (DATA_WIDTH),
    .DEPTH      (DEPTH)
  ) fifo (
    .data    (enq_bits),
    .rdclk   (read_clock),
    .rdreq   (read_request),
    .wrclk   (write_clock),
    .wrreq   (write_request),
    .q       (deq_bits),
    .rdempty (read_empty),
    .wrfull  (write_full)
  );

  assign deq_valid = ~read_empty;
  assign enq_ready = ~write_full;
endmodule
