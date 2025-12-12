module Queue64_UInt(
  input         clock,
  input         reset,
  input         io_enq_valid,
  input  [63:0] io_enq_bits,
  input         io_deq_ready,
  output        io_deq_valid,
  output [63:0] io_deq_bits,
  output [6:0]  io_count,
  input         io_flush
);

  wire       io_enq_ready;
  wire       _ram_ext_R0_en = 1'h1;
  reg  [5:0] enq_ptr_value;
  reg  [5:0] deq_ptr_value;
  reg        maybe_full;
  wire       ptr_match = enq_ptr_value == deq_ptr_value;
  wire       empty = ptr_match & ~maybe_full;
  wire       full = ptr_match & maybe_full;
  wire       do_enq = io_enq_ready & io_enq_valid;
  wire       do_deq = io_deq_ready & ~empty;
  assign io_enq_ready = ~full;
  wire [5:0] deq_ptr_next = (&deq_ptr_value) ? 6'h0 : 6'(deq_ptr_value + 6'h1);
  wire [5:0] r_addr = do_deq ? deq_ptr_next : deq_ptr_value;
  wire       _GEN = do_enq == do_deq ? maybe_full : do_enq;
  always @(posedge clock) begin
    if (reset) begin
      enq_ptr_value <= 6'h0;
      deq_ptr_value <= 6'h0;
      maybe_full <= 1'h0;
    end
    else begin
      if (io_flush) begin
        enq_ptr_value <= 6'h0;
        deq_ptr_value <= 6'h0;
      end
      else begin
        if (do_enq)
          enq_ptr_value <= 6'(enq_ptr_value + 6'h1);
        if (do_deq)
          deq_ptr_value <= 6'(deq_ptr_value + 6'h1);
      end
      maybe_full <= ~io_flush & _GEN;
    end
  end // always @(posedge)
  ram_64x64 ram_ext (
    .R0_addr (r_addr),
    .R0_en   (_ram_ext_R0_en),
    .R0_clk  (clock),
    .R0_data (io_deq_bits),
    .W0_addr (enq_ptr_value),
    .W0_en   (do_enq),
    .W0_clk  (clock),
    .W0_data (io_enq_bits)
  );
  assign io_deq_valid = ~empty;
  assign io_count = {full, 6'(enq_ptr_value - deq_ptr_value)};
endmodule

