// Shared single-port RAM wrapper around the existing Arcadia VHDL RAM block.
module CaveSinglePortRam #(
  parameter ADDR_WIDTH = 8,
  parameter DATA_WIDTH = 8,
  parameter DEPTH = 0,
  parameter MASK_ENABLE = 0
) (
  input                         clock,
  input                         rd,
  input                         wr,
  input      [ADDR_WIDTH-1:0]   addr,
  input      [DATA_WIDTH/8-1:0] mask,
  input      [DATA_WIDTH-1:0]   din,
  output     [DATA_WIDTH-1:0]   dout
);
  generate
    if (MASK_ENABLE) begin : masked
      single_port_ram #(
        .ADDR_WIDTH  (ADDR_WIDTH),
        .DATA_WIDTH  (DATA_WIDTH),
        .DEPTH       (DEPTH),
        .MASK_ENABLE ("TRUE")
      ) ram (
        .clk  (clock),
        .rd   (rd),
        .wr   (wr),
        .addr (addr),
        .mask (mask),
        .din  (din),
        .dout (dout)
      );
    end
    else begin : unmasked
      single_port_ram #(
        .ADDR_WIDTH  (ADDR_WIDTH),
        .DATA_WIDTH  (DATA_WIDTH),
        .DEPTH       (DEPTH),
        .MASK_ENABLE ("FALSE")
      ) ram (
        .clk  (clock),
        .rd   (rd),
        .wr   (wr),
        .addr (addr),
        .mask (mask),
        .din  (din),
        .dout (dout)
      );
    end
  endgenerate
endmodule
