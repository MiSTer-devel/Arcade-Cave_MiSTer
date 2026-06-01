// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// 16-byte parallel-in, serial-out buffer used by the sprite blitter.
module CaveSpritePixelShiftBuffer (
  input        clock,
  input        reset,
  input        io_rd,
  input        io_wr,
  output       io_isEmpty,
  output       io_isAlmostEmpty,
  input  [7:0] io_din_0,
  input  [7:0] io_din_1,
  input  [7:0] io_din_2,
  input  [7:0] io_din_3,
  input  [7:0] io_din_4,
  input  [7:0] io_din_5,
  input  [7:0] io_din_6,
  input  [7:0] io_din_7,
  input  [7:0] io_din_8,
  input  [7:0] io_din_9,
  input  [7:0] io_din_10,
  input  [7:0] io_din_11,
  input  [7:0] io_din_12,
  input  [7:0] io_din_13,
  input  [7:0] io_din_14,
  input  [7:0] io_din_15,
  output [7:0] io_dout
);
  reg [127:0] shift_reg;
  reg [4:0]   count_reg;

  wire read_valid = io_rd & (|count_reg);

  always @(posedge clock) begin
    if (io_wr)
      shift_reg <= {
        io_din_15, io_din_14, io_din_13, io_din_12,
        io_din_11, io_din_10, io_din_9,  io_din_8,
        io_din_7,  io_din_6,  io_din_5,  io_din_4,
        io_din_3,  io_din_2,  io_din_1,  io_din_0
      };
    else if (read_valid)
      shift_reg <= {shift_reg[7:0], shift_reg[127:8]};

    if (reset)
      count_reg <= 5'h00;
    else if (io_wr)
      count_reg <= 5'h10;
    else if (read_valid)
      count_reg <= count_reg - 5'h01;
  end

  assign io_isEmpty = ~(|count_reg);
  assign io_isAlmostEmpty = count_reg == 5'h01;
  assign io_dout = shift_reg[7:0];
endmodule
