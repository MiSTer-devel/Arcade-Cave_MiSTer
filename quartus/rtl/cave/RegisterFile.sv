module RegisterFile(
  input         clock,
  input         io_mem_wr,
  input  [1:0]  io_mem_addr,
  input  [15:0] io_mem_din,
  output [15:0] io_regs_0
);

  reg  [15:0] regs_0;
  reg  [15:0] regs_1;
  reg  [15:0] regs_2;
  reg  [15:0] regs_3;
  reg  [15:0] casez_tmp;
  always @(*) begin
    casez (io_mem_addr)
      2'b00:
        casez_tmp = regs_0;
      2'b01:
        casez_tmp = regs_1;
      2'b10:
        casez_tmp = regs_2;
      default:
        casez_tmp = regs_3;
    endcase
  end // always @(*)
  wire [7:0]  bytes_1 = io_mem_wr ? io_mem_din[15:8] : casez_tmp[15:8];
  wire [7:0]  bytes_0 = io_mem_wr ? io_mem_din[7:0] : casez_tmp[7:0];
  wire [15:0] _regs_T = {bytes_1, bytes_0};
  always @(posedge clock) begin
    if (io_mem_addr == 2'h0)
      regs_0 <= _regs_T;
    if (io_mem_addr == 2'h1)
      regs_1 <= _regs_T;
    if (io_mem_addr == 2'h2)
      regs_2 <= _regs_T;
    if (&io_mem_addr)
      regs_3 <= _regs_T;
  end // always @(posedge)
  assign io_regs_0 = regs_0;
endmodule

