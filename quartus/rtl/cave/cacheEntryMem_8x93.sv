// VCS coverage exclude_file
module cacheEntryMem_8x93(
  input  [2:0]  R0_addr,
  input         R0_en,
  input         R0_clk,
  output [92:0] R0_data,
  input  [2:0]  W0_addr,
  input         W0_en,
  input         W0_clk,
  input  [92:0] W0_data
);

  reg [92:0] Memory[0:7];
  reg        _R0_en_d0;
  reg [2:0]  _R0_addr_d0;
  always @(posedge R0_clk) begin
    _R0_en_d0 <= R0_en;
    _R0_addr_d0 <= R0_addr;
  end // always @(posedge)
  always @(posedge W0_clk) begin
    if (W0_en)
      Memory[W0_addr] <= W0_data;
  end // always @(posedge)
  assign R0_data = _R0_en_d0 ? Memory[_R0_addr_d0] : 93'bx;
endmodule

