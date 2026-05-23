// Shared true-dual-port RAM wrapper around the existing Arcadia VHDL RAM block.
module CaveTrueDualPortRam #(
  parameter ADDR_WIDTH_A = 8,
  parameter ADDR_WIDTH_B = 8,
  parameter DATA_WIDTH_A = 8,
  parameter DATA_WIDTH_B = 8,
  parameter DEPTH_A = 0,
  parameter DEPTH_B = 0,
  parameter MASK_ENABLE = 0
) (
  input                           clock_a,
  input                           rd_a,
  input                           wr_a,
  input      [ADDR_WIDTH_A-1:0]   addr_a,
  input      [DATA_WIDTH_A/8-1:0] mask_a,
  input      [DATA_WIDTH_A-1:0]   din_a,
  output     [DATA_WIDTH_A-1:0]   dout_a,
  input                           clock_b,
  input                           rd_b,
  input      [ADDR_WIDTH_B-1:0]   addr_b,
  output     [DATA_WIDTH_B-1:0]   dout_b
);
  generate
    if (MASK_ENABLE) begin : masked
      true_dual_port_ram #(
        .ADDR_WIDTH_A (ADDR_WIDTH_A),
        .ADDR_WIDTH_B (ADDR_WIDTH_B),
        .DATA_WIDTH_A (DATA_WIDTH_A),
        .DATA_WIDTH_B (DATA_WIDTH_B),
        .DEPTH_A      (DEPTH_A),
        .DEPTH_B      (DEPTH_B),
        .MASK_ENABLE  ("TRUE")
      ) ram (
        .clk_a  (clock_a),
        .rd_a   (rd_a),
        .wr_a   (wr_a),
        .addr_a (addr_a),
        .mask_a (mask_a),
        .din_a  (din_a),
        .dout_a (dout_a),
        .clk_b  (clock_b),
        .rd_b   (rd_b),
        .addr_b (addr_b),
        .dout_b (dout_b)
      );
    end
    else begin : unmasked
      true_dual_port_ram #(
        .ADDR_WIDTH_A (ADDR_WIDTH_A),
        .ADDR_WIDTH_B (ADDR_WIDTH_B),
        .DATA_WIDTH_A (DATA_WIDTH_A),
        .DATA_WIDTH_B (DATA_WIDTH_B),
        .DEPTH_A      (DEPTH_A),
        .DEPTH_B      (DEPTH_B),
        .MASK_ENABLE  ("FALSE")
      ) ram (
        .clk_a  (clock_a),
        .rd_a   (rd_a),
        .wr_a   (wr_a),
        .addr_a (addr_a),
        .mask_a (mask_a),
        .din_a  (din_a),
        .dout_a (dout_a),
        .clk_b  (clock_b),
        .rd_b   (rd_b),
        .addr_b (addr_b),
        .dout_b (dout_b)
      );
    end
  endgenerate
endmodule
