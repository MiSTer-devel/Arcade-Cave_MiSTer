module SinglePortRam(
  input         clock,
  input         io_rd,
  input         io_wr,
  input  [14:0] io_addr,
  input  [1:0]  io_mask,
  input  [15:0] io_din,
  output [15:0] io_dout
);

  single_port_ram #(
    .ADDR_WIDTH(15),
    .DATA_WIDTH(16),
    .DEPTH(0),
    .MASK_ENABLE("TRUE")
  ) ram (
    .clk  (clock),
    .rd   (io_rd),
    .wr   (io_wr),
    .addr (io_addr),
    .mask (io_mask),
    .din  (io_din),
    .dout (io_dout)
  );
endmodule

