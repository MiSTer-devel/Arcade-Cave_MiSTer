// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Shared single-clock queue used by the retired Chisel Queue wrappers.
module CaveSyncQueue #(
  parameter ADDR_WIDTH = 5,
  parameter DATA_WIDTH = 64,
  parameter DEPTH = (1 << ADDR_WIDTH)
) (
  input                   clock,
  input                   reset,
  output                  io_enq_ready,
  input                   io_enq_valid,
  input  [DATA_WIDTH-1:0] io_enq_bits,
  input                   io_deq_ready,
  output                  io_deq_valid,
  output [DATA_WIDTH-1:0] io_deq_bits,
  output [ADDR_WIDTH:0]   io_count,
  input                   io_flush
);
  reg [ADDR_WIDTH-1:0] enq_ptr_value;
  reg [ADDR_WIDTH-1:0] deq_ptr_value;
  reg                  maybe_full;

  wire ptr_match = enq_ptr_value == deq_ptr_value;
  wire empty = ptr_match & ~maybe_full;
  wire full = ptr_match & maybe_full;

  wire do_enq = io_enq_ready & io_enq_valid;
  wire do_deq = io_deq_ready & ~empty;
  wire [ADDR_WIDTH-1:0] deq_ptr_next = deq_ptr_value + 1'b1;
  wire [ADDR_WIDTH-1:0] read_addr = do_deq ? deq_ptr_next : deq_ptr_value;
  wire maybe_full_next = do_enq == do_deq ? maybe_full : do_enq;
  wire [ADDR_WIDTH-1:0] ptr_distance = enq_ptr_value - deq_ptr_value;

  always @(posedge clock) begin
    if (reset) begin
      enq_ptr_value <= {ADDR_WIDTH{1'b0}};
      deq_ptr_value <= {ADDR_WIDTH{1'b0}};
      maybe_full <= 1'b0;
    end
    else begin
      if (io_flush) begin
        enq_ptr_value <= {ADDR_WIDTH{1'b0}};
        deq_ptr_value <= {ADDR_WIDTH{1'b0}};
      end
      else begin
        if (do_enq)
          enq_ptr_value <= enq_ptr_value + 1'b1;
        if (do_deq)
          deq_ptr_value <= deq_ptr_value + 1'b1;
      end

      maybe_full <= ~io_flush & maybe_full_next;
    end
  end

  CaveSyncReadMem #(
    .ADDR_WIDTH (ADDR_WIDTH),
    .DATA_WIDTH (DATA_WIDTH),
    .DEPTH      (DEPTH)
  ) ram (
    .read_addr  (read_addr),
    .read_en    (1'b1),
    .read_clk   (clock),
    .read_data  (io_deq_bits),
    .write_addr (enq_ptr_value),
    .write_en   (do_enq),
    .write_clk  (clock),
    .write_data (io_enq_bits)
  );

  assign io_enq_ready = ~full;
  assign io_deq_valid = ~empty;
  assign io_count = {full, ptr_distance};
endmodule
