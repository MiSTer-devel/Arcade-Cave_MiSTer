module AsyncMemArbiter(
  input         clock,
  input         reset,
  input         io_in_0_rd,
  input         io_in_0_wr,
  input  [6:0]  io_in_0_addr,
  input  [15:0] io_in_0_din,
  output [15:0] io_in_0_dout,
  output        io_in_0_wait_n,
  output        io_in_0_valid,
  input         io_in_1_rd,
  input         io_in_1_wr,
  input  [6:0]  io_in_1_addr,
  input  [15:0] io_in_1_din,
  output [15:0] io_in_1_dout,
  output        io_in_1_wait_n,
  output        io_in_1_valid,
  output        io_out_rd,
  output        io_out_wr,
  output [6:0]  io_out_addr,
  output [15:0] io_out_din,
  input  [15:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid
);

  reg         busyReg;
  reg  [1:0]  indexReg;
  wire [1:0]  index_enc =
    io_in_0_rd | io_in_0_wr ? 2'h1 : {io_in_1_rd | io_in_1_wr, 1'h0};
  wire [1:0]  chosen = busyReg ? indexReg : index_enc;
  wire        io_out_rd_0 = chosen[0] & io_in_0_rd | chosen[1] & io_in_1_rd;
  wire [6:0]  _io_out_mem_addr_T = chosen[0] ? io_in_0_addr : 7'h0;
  wire [6:0]  _io_out_mem_addr_T_1 = chosen[1] ? io_in_1_addr : 7'h0;
  wire [15:0] _io_out_mem_din_T = chosen[0] ? io_in_0_din : 16'h0;
  wire [15:0] _io_out_mem_din_T_1 = chosen[1] ? io_in_1_din : 16'h0;
  wire        _io_out_io_in_1_wait_n_T = chosen == 2'h0;
  wire        effectiveRequest = ~busyReg & io_out_rd_0 & io_out_wait_n;
  always @(posedge clock) begin
    if (reset) begin
      busyReg <= 1'h0;
      indexReg <= 2'h0;
    end
    else begin
      busyReg <= ~io_out_valid & (effectiveRequest | busyReg);
      if (io_out_valid | ~effectiveRequest) begin
      end
      else
        indexReg <= index_enc;
    end
  end // always @(posedge)
  assign io_in_0_dout = io_out_dout;
  assign io_in_0_wait_n = (_io_out_io_in_1_wait_n_T | chosen[0]) & io_out_wait_n;
  assign io_in_0_valid = chosen[0] & io_out_valid;
  assign io_in_1_dout = io_out_dout;
  assign io_in_1_wait_n = (_io_out_io_in_1_wait_n_T | chosen[1]) & io_out_wait_n;
  assign io_in_1_valid = chosen[1] & io_out_valid;
  assign io_out_rd = io_out_rd_0;
  assign io_out_wr = chosen[0] & io_in_0_wr | chosen[1] & io_in_1_wr;
  assign io_out_addr = _io_out_mem_addr_T | _io_out_mem_addr_T_1;
  assign io_out_din = _io_out_mem_din_T | _io_out_mem_din_T_1;
endmodule

