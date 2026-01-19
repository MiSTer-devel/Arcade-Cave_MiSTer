module NMK112(
  input         clock,
  input         io_cpu_wr,
  input  [22:0] io_cpu_addr,
  input  [15:0] io_cpu_din,
  input  [24:0] io_addr_0_in,
  output [24:0] io_addr_0_out,
  input  [24:0] io_addr_1_in,
  output [24:0] io_addr_1_out
);

  reg  [4:0] pageTableReg_0_0;
  reg  [4:0] pageTableReg_0_1;
  reg  [4:0] pageTableReg_0_2;
  reg  [4:0] pageTableReg_0_3;
  reg  [4:0] pageTableReg_1_0;
  reg  [4:0] pageTableReg_1_1;
  reg  [4:0] pageTableReg_1_2;
  reg  [4:0] pageTableReg_1_3;
  reg  [4:0] casez_tmp;
  always @(*) begin
    casez (io_addr_0_in[17:16])
      2'b00:
        casez_tmp = pageTableReg_0_0;
      2'b01:
        casez_tmp = pageTableReg_0_1;
      2'b10:
        casez_tmp = pageTableReg_0_2;
      default:
        casez_tmp = pageTableReg_0_3;
    endcase
  end // always @(*)
  reg  [4:0] casez_tmp_0;
  wire [1:0] io_addr_1_out_bank =
    io_addr_1_in > 25'h400 ? io_addr_1_in[17:16] : io_addr_1_in[9:8];
  always @(*) begin
    casez (io_addr_1_out_bank)
      2'b00:
        casez_tmp_0 = pageTableReg_1_0;
      2'b01:
        casez_tmp_0 = pageTableReg_1_1;
      2'b10:
        casez_tmp_0 = pageTableReg_1_2;
      default:
        casez_tmp_0 = pageTableReg_1_3;
    endcase
  end // always @(*)
  wire       _GEN = io_cpu_addr[1:0] == 2'h0;
  wire       _GEN_0 = io_cpu_addr[1:0] == 2'h1;
  wire       _GEN_1 = io_cpu_addr[1:0] == 2'h2;
  always @(posedge clock) begin
    if (io_cpu_wr & ~(io_cpu_addr[2]) & _GEN)
      pageTableReg_0_0 <= io_cpu_din[4:0];
    if (io_cpu_wr & ~(io_cpu_addr[2]) & _GEN_0)
      pageTableReg_0_1 <= io_cpu_din[4:0];
    if (io_cpu_wr & ~(io_cpu_addr[2]) & _GEN_1)
      pageTableReg_0_2 <= io_cpu_din[4:0];
    if (io_cpu_wr & ~(io_cpu_addr[2]) & (&(io_cpu_addr[1:0])))
      pageTableReg_0_3 <= io_cpu_din[4:0];
    if (io_cpu_wr & io_cpu_addr[2] & _GEN)
      pageTableReg_1_0 <= io_cpu_din[4:0];
    if (io_cpu_wr & io_cpu_addr[2] & _GEN_0)
      pageTableReg_1_1 <= io_cpu_din[4:0];
    if (io_cpu_wr & io_cpu_addr[2] & _GEN_1)
      pageTableReg_1_2 <= io_cpu_din[4:0];
    if (io_cpu_wr & io_cpu_addr[2] & (&(io_cpu_addr[1:0])))
      pageTableReg_1_3 <= io_cpu_din[4:0];
  end // always @(posedge)
  assign io_addr_0_out = {4'h0, casez_tmp, io_addr_0_in[15:0]};
  assign io_addr_1_out = {4'h0, casez_tmp_0, io_addr_1_in[15:0]};
endmodule

