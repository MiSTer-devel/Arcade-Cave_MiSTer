module TrueDualPortRam_4(
  input         clock,
  input         io_clockB,
  input         io_portA_rd,
  input         io_portA_wr,
  input  [10:0] io_portA_addr,
  input  [1:0]  io_portA_mask,
  input  [15:0] io_portA_din,
  output [15:0] io_portA_dout,
  input  [9:0]  io_portB_addr,
  output [31:0] io_portB_dout
);

  wire _ram_rd_b = 1'h1;
  true_dual_port_ram #(
    .ADDR_WIDTH_A(11),
    .ADDR_WIDTH_B(10),
    .DATA_WIDTH_A(16),
    .DATA_WIDTH_B(32),
    .DEPTH_A(0),
    .DEPTH_B(0),
    .MASK_ENABLE("TRUE")
  ) ram (
    .clk_a  (clock),
    .rd_a   (io_portA_rd),
    .wr_a   (io_portA_wr),
    .addr_a (io_portA_addr),
    .mask_a (io_portA_mask),
    .din_a  (io_portA_din),
    .dout_a (io_portA_dout),
    .clk_b  (io_clockB),
    .rd_b   (_ram_rd_b),
    .addr_b (io_portB_addr),
    .dout_b (io_portB_dout)
  );
endmodule

