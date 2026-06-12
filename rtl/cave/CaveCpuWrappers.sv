// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module CaveMain68kCpu(
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
  reg phi1_enable;
  reg phi2_enable;

  wire halt_n = ~io_halt;
  wire dtack_n = ~io_dtack;
  wire vpa_n = ~io_vpa;
  wire as_n;
  wire uds_n;
  wire lds_n;
  wire fc0;
  wire fc1;
  wire fc2;

  always @(posedge clock) begin
    if (reset)
      phi1_enable <= 1'b0;
    else
      phi1_enable <= ~phi1_enable;

    phi2_enable <= phi1_enable;
  end

  fx68k cpu (
    .clk      (clock),
    .enPhi1   (phi1_enable),
    .enPhi2   (phi2_enable),
    .extReset (reset),
    .pwrUp    (reset),
    .HALTn    (halt_n),
    .ASn      (as_n),
    .eRWn     (io_rw),
    .UDSn     (uds_n),
    .LDSn     (lds_n),
    .DTACKn   (dtack_n),
    .BERRn    (1'b1),
    .E        (),
    .VPAn     (vpa_n),
    .VMAn     (),
    .BRn      (1'b1),
    .BGn      (),
    .BGACKn   (1'b1),
    .IPL0n    (~io_ipl[0]),
    .IPL1n    (~io_ipl[1]),
    .IPL2n    (~io_ipl[2]),
    .FC0      (fc0),
    .FC1      (fc1),
    .FC2      (fc2),
    .eab      (io_addr),
    .iEdb     (io_din),
    .oEdb     (io_dout)
  );

  assign io_as = ~as_n;
  assign io_uds = ~uds_n;
  assign io_lds = ~lds_n;
  assign io_fc = {fc2, fc1, fc0};
endmodule

module CaveSoundZ80Cpu(
  input         clock,
  input         reset,
  output [15:0] io_addr,
  input  [7:0]  io_din,
  output [7:0]  io_dout,
  output        io_rd,
  output        io_wr,
  output        io_rfsh,
  output        io_mreq,
  output        io_iorq,
  input         io_fast_clock,
  input         io_wait_n,
  input         io_int,
  input         io_nmi
);
  reg [2:0] clock_divider;
  wire cpu_clock_enable = io_fast_clock ? &clock_divider[1:0] : &clock_divider;

  wire mreq_n;
  wire iorq_n;
  wire rd_n;
  wire wr_n;
  wire rfsh_n;

  always @(posedge clock) begin
    if (reset)
      clock_divider <= 3'd0;
    else
      clock_divider <= clock_divider + 3'd1;
  end

  T80s cpu (
    .RESET_n (~reset),
    .CLK     (clock),
    .CEN     (cpu_clock_enable),
    .WAIT_n  (io_wait_n),
    .INT_n   (~io_int),
    .NMI_n   (~io_nmi),
    .BUSRQ_n (1'b1),
    .M1_n    (),
    .MREQ_n  (mreq_n),
    .IORQ_n  (iorq_n),
    .RD_n    (rd_n),
    .WR_n    (wr_n),
    .RFSH_n  (rfsh_n),
    .HALT_n  (),
    .BUSAK_n (),
    .A       (io_addr),
    .DI      (io_din),
    .DO      (io_dout),
    .REG     ()
  );

  assign io_rd = ~rd_n;
  assign io_wr = ~wr_n;
  assign io_rfsh = ~rfsh_n;
  assign io_mreq = ~mreq_n;
  assign io_iorq = ~iorq_n;
endmodule
