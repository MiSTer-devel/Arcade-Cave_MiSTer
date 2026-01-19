module CPU(
  input         clock,
  input         reset,
  input         io_halt,
  output        io_as,
  output        io_rw,
  output        io_uds,
  output        io_lds,
  input         io_dtack,
  input         io_vpa,
  input  [2:0]  io_ipl,
  output [2:0]  io_fc,
  output [22:0] io_addr,
  input  [15:0] io_din,
  output [15:0] io_dout
);

  wire _cpu_HALTn;
  wire _cpu_DTACKn;
  wire _cpu_BERRn = 1'h1;
  wire _cpu_VPAn;
  wire _cpu_BRn = 1'h1;
  wire _cpu_BGACKn = 1'h1;
  wire _cpu_IPL0n;
  wire _cpu_IPL1n;
  wire _cpu_IPL2n;
  wire _cpu_ASn;
  wire _cpu_UDSn;
  wire _cpu_LDSn;
  wire _cpu_FC0;
  wire _cpu_FC1;
  wire _cpu_FC2;
  reg  phi1_value;
  reg  phi2;
  always @(posedge clock) begin
    if (reset)
      phi1_value <= 1'h0;
    else
      phi1_value <= 1'(phi1_value - 1'h1);
    phi2 <= phi1_value;
  end // always @(posedge)
  assign _cpu_HALTn = ~io_halt;
  assign _cpu_DTACKn = ~io_dtack;
  assign _cpu_VPAn = ~io_vpa;
  assign _cpu_IPL0n = ~(io_ipl[0]);
  assign _cpu_IPL1n = ~(io_ipl[1]);
  assign _cpu_IPL2n = ~(io_ipl[2]);
  fx68k cpu (
    .clk      (clock),
    .enPhi1   (phi1_value),
    .enPhi2   (phi2),
    .extReset (reset),
    .pwrUp    (reset),
    .HALTn    (_cpu_HALTn),
    .ASn      (_cpu_ASn),
    .eRWn     (io_rw),
    .UDSn     (_cpu_UDSn),
    .LDSn     (_cpu_LDSn),
    .DTACKn   (_cpu_DTACKn),
    .BERRn    (_cpu_BERRn),
    .E        (/* unused */),
    .VPAn     (_cpu_VPAn),
    .VMAn     (/* unused */),
    .BRn      (_cpu_BRn),
    .BGn      (/* unused */),
    .BGACKn   (_cpu_BGACKn),
    .IPL0n    (_cpu_IPL0n),
    .IPL1n    (_cpu_IPL1n),
    .IPL2n    (_cpu_IPL2n),
    .FC0      (_cpu_FC0),
    .FC1      (_cpu_FC1),
    .FC2      (_cpu_FC2),
    .eab      (io_addr),
    .iEdb     (io_din),
    .oEdb     (io_dout)
  );
  assign io_as = ~_cpu_ASn;
  assign io_uds = ~_cpu_UDSn;
  assign io_lds = ~_cpu_LDSn;
  assign io_fc = {_cpu_FC2, _cpu_FC1, _cpu_FC0};
endmodule

