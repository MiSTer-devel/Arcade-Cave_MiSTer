module Sound(
  input         clock,
  input         reset,
  input         io_ctrl_oki_0_wr,
  input  [15:0] io_ctrl_oki_0_din,
  output [15:0] io_ctrl_oki_0_dout,
  input         io_ctrl_oki_1_wr,
  input  [15:0] io_ctrl_oki_1_din,
  output [15:0] io_ctrl_oki_1_dout,
  input         io_ctrl_nmk_wr,
  input  [22:0] io_ctrl_nmk_addr,
  input  [15:0] io_ctrl_nmk_din,
  input         io_ctrl_ymz_rd,
  input         io_ctrl_ymz_wr,
  input  [22:0] io_ctrl_ymz_addr,
  input  [15:0] io_ctrl_ymz_din,
  output [15:0] io_ctrl_ymz_dout,
  input         io_ctrl_req,
  input  [15:0] io_ctrl_data,
  output        io_ctrl_irq,
  input  [3:0]  io_gameIndex,
  input  [1:0]  io_gameConfig_sound_0_device,
  output        io_rom_0_rd,
  output [24:0] io_rom_0_addr,
  input  [7:0]  io_rom_0_dout,
  input         io_rom_0_wait_n,
  input         io_rom_0_valid,
  output [24:0] io_rom_1_addr,
  input  [7:0]  io_rom_1_dout,
  input         io_rom_1_valid,
  output [15:0] io_audio
);

  wire        cs_1;
  wire        cs;
  wire        _GEN;
  wire        _arbiter_io_in_0_rd;
  wire        _arbiter_io_in_1_rd;
  wire [24:0] _arbiter_io_in_1_addr;
  wire        _arbiter_io_in_2_rd;
  wire [24:0] _arbiter_io_in_2_addr;
  wire        _arbiter_io_in_3_rd;
  wire [24:0] _arbiter_io_in_3_addr;
  wire [7:0]  _arbiter_io_in_2_dout;
  wire [7:0]  _arbiter_io_in_3_dout;
  wire        _ym2203_io_cpu_wr;
  wire        _ym2203_io_cpu_addr;
  wire [7:0]  _ym2203_io_cpu_dout;
  wire        _ym2203_io_audio_valid;
  wire [15:0] _ym2203_io_audio_bits_psg;
  wire [15:0] _ym2203_io_audio_bits_fm;
  wire        _ymz280b_io_cpu_addr;
  wire [7:0]  _ymz280b_io_cpu_din;
  wire [7:0]  _ymz280b_io_rom_dout;
  wire        _ymz280b_io_rom_wait_n;
  wire        _ymz280b_io_rom_valid;
  wire [7:0]  _ymz280b_io_cpu_dout;
  wire        _ymz280b_io_rom_rd;
  wire [23:0] _ymz280b_io_rom_addr;
  wire        _ymz280b_io_audio_valid;
  wire [15:0] _ymz280b_io_audio_bits_left;
  wire [7:0]  _oki_1_io_cpu_dout;
  wire [17:0] _oki_1_io_rom_addr;
  wire        _oki_1_io_audio_valid;
  wire [13:0] _oki_1_io_audio_bits;
  wire [7:0]  _oki_0_io_cpu_din;
  wire [7:0]  _oki_0_io_rom_dout;
  wire        _oki_0_io_rom_valid;
  wire [7:0]  _oki_0_io_cpu_dout;
  wire [17:0] _oki_0_io_rom_addr;
  wire        _oki_0_io_audio_valid;
  wire [13:0] _oki_0_io_audio_bits;
  wire [24:0] _nmk_io_addr_0_in;
  wire [24:0] _nmk_io_addr_1_in;
  wire [24:0] _nmk_io_addr_0_out;
  wire [24:0] _nmk_io_addr_1_out;
  wire        _soundRam_io_rd;
  wire        _soundRam_io_wr;
  wire [12:0] _soundRam_io_addr;
  wire [7:0]  _soundRam_io_dout;
  wire        _cpu_io_int;
  wire [15:0] _cpu_io_addr;
  wire [7:0]  _cpu_io_dout;
  wire        _cpu_io_rd;
  wire        _cpu_io_wr;
  wire        _cpu_io_rfsh;
  wire        _cpu_io_mreq;
  wire        _cpu_io_iorq;
  reg         reqReg;
  reg  [15:0] dataReg;
  reg  [3:0]  z80BankReg;
  reg  [3:0]  okiBankHiReg;
  reg  [3:0]  okiBankLoReg;
  wire        _mem_addr_T_4 = io_gameIndex == 4'h2;
  wire [3:0]  mem_addr_bank = _oki_0_io_rom_addr[17] ? okiBankHiReg : okiBankLoReg;
  wire [24:0] mem_addr =
    _mem_addr_T_4 ? _nmk_io_addr_0_out : {4'h0, mem_addr_bank, _oki_0_io_rom_addr[16:0]};
  wire [3:0]  mem_addr_bank_1 = _oki_1_io_rom_addr[17] ? okiBankHiReg : okiBankLoReg;
  assign _GEN = io_gameIndex == 4'h7;
  assign cs = _cpu_io_addr < 16'h4000;
  assign cs_1 = (|(_cpu_io_addr[15:14])) & ~(_cpu_io_addr[15]);
  wire [7:0]  _GEN_0 =
    cs_1 & _cpu_io_mreq & _cpu_io_rd ? _arbiter_io_in_3_dout : _arbiter_io_in_2_dout;
  wire        cs_2 = _cpu_io_addr > 16'hDFFF;
  wire        _soundRam_io_wr_T = cs_2 & _cpu_io_mreq;
  wire [7:0]  _GEN_1 = _soundRam_io_wr_T & _cpu_io_rd ? _soundRam_io_dout : _GEN_0;
  wire [15:0] _GEN_2 = {8'h0, _cpu_io_addr[7:0]};
  wire        _GEN_3 = _GEN_2 > 16'h2F & _GEN_2 < 16'h31 & _cpu_io_iorq & _cpu_io_rd;
  wire [7:0]  _GEN_4 = _GEN_3 ? dataReg[7:0] : _GEN_1;
  wire        _GEN_5 =
    (|(_cpu_io_addr[7:6])) & _GEN_2 < 16'h41 & _cpu_io_iorq & _cpu_io_rd;
  wire [7:0]  _GEN_6 = _GEN_5 ? dataReg[15:8] : _GEN_4;
  wire        _ym2203_io_cpu_wr_T = _GEN_2 > 16'h4F & _GEN_2 < 16'h52 & _cpu_io_iorq;
  wire [7:0]  _GEN_7 = _ym2203_io_cpu_wr_T & _cpu_io_rd ? _ym2203_io_cpu_dout : _GEN_6;
  wire        _oki_1_io_cpu_wr_T = _GEN_2 > 16'h5F & _GEN_2 < 16'h61 & _cpu_io_iorq;
  wire        oki_1_io_cpu_wr = _GEN ? _oki_1_io_cpu_wr_T & _cpu_io_wr : io_ctrl_oki_1_wr;
  wire [7:0]  oki_1_io_cpu_din = _GEN ? _cpu_io_dout : io_ctrl_oki_1_din[7:0];
  wire [7:0]  cpu_io_din = _oki_1_io_cpu_wr_T & _cpu_io_rd ? _oki_1_io_cpu_dout : _GEN_7;
  reg  [15:0] io_audio_r;
  reg  [15:0] io_audio_r_1;
  reg  [15:0] io_audio_r_2;
  reg  [13:0] io_audio_r_3;
  reg  [13:0] io_audio_r_4;
  always @(posedge clock) begin
    if (reset) begin
      reqReg <= 1'h0;
      z80BankReg <= 4'h0;
      okiBankHiReg <= 4'h0;
      okiBankLoReg <= 4'h0;
    end
    else begin
      reqReg <= ~(_GEN & (_GEN_5 | _GEN_3)) & (io_ctrl_req | reqReg);
      if (_GEN & _cpu_io_addr[7:0] == 8'h0 & _cpu_io_iorq & _cpu_io_wr)
        z80BankReg <= _cpu_io_dout[3:0];
      if (_GEN & _GEN_2 > 16'h6F & _GEN_2 < 16'h71 & _cpu_io_iorq & _cpu_io_wr) begin
        okiBankHiReg <= {2'h0, _cpu_io_dout[5:4]};
        okiBankLoReg <= {2'h0, _cpu_io_dout[1:0]};
      end
    end
    if (io_ctrl_req)
      dataReg <= io_ctrl_data;
    if (_ymz280b_io_audio_valid)
      io_audio_r <= _ymz280b_io_audio_bits_left;
    if (_ym2203_io_audio_valid) begin
      io_audio_r_1 <= _ym2203_io_audio_bits_psg;
      io_audio_r_2 <= _ym2203_io_audio_bits_fm;
    end
    if (_oki_0_io_audio_valid)
      io_audio_r_3 <= _oki_0_io_audio_bits;
    if (_oki_1_io_audio_valid)
      io_audio_r_4 <= _oki_1_io_audio_bits;
  end // always @(posedge)
  CPU_1 cpu (
    .clock   (clock),
    .reset   (reset),
    .io_addr (_cpu_io_addr),
    .io_din  (cpu_io_din),
    .io_dout (_cpu_io_dout),
    .io_rd   (_cpu_io_rd),
    .io_wr   (_cpu_io_wr),
    .io_rfsh (_cpu_io_rfsh),
    .io_mreq (_cpu_io_mreq),
    .io_iorq (_cpu_io_iorq),
    .io_int  (_cpu_io_int),
    .io_nmi  (reqReg)
  );
  assign _soundRam_io_rd = _GEN & cs_2 & ~_cpu_io_rfsh;
  assign _soundRam_io_wr = _GEN & _soundRam_io_wr_T & _cpu_io_wr;
  assign _soundRam_io_addr = _cpu_io_addr[12:0];
  SinglePortRam_1 soundRam (
    .clock   (clock),
    .io_rd   (_soundRam_io_rd),
    .io_wr   (_soundRam_io_wr),
    .io_addr (_soundRam_io_addr),
    .io_din  (_cpu_io_dout),
    .io_dout (_soundRam_io_dout)
  );
  assign _nmk_io_addr_0_in = {7'h0, _oki_0_io_rom_addr};
  assign _nmk_io_addr_1_in = {7'h0, _oki_1_io_rom_addr};
  NMK112 nmk (
    .clock         (clock),
    .io_cpu_wr     (io_ctrl_nmk_wr),
    .io_cpu_addr   (io_ctrl_nmk_addr),
    .io_cpu_din    (io_ctrl_nmk_din),
    .io_addr_0_in  (_nmk_io_addr_0_in),
    .io_addr_0_out (_nmk_io_addr_0_out),
    .io_addr_1_in  (_nmk_io_addr_1_in),
    .io_addr_1_out (_nmk_io_addr_1_out)
  );
  assign _oki_0_io_cpu_din = io_ctrl_oki_0_din[7:0];
  OKIM6295 oki_0 (
    .clock          (clock),
    .reset          (reset),
    .io_cpu_wr      (io_ctrl_oki_0_wr),
    .io_cpu_din     (_oki_0_io_cpu_din),
    .io_cpu_dout    (_oki_0_io_cpu_dout),
    .io_rom_addr    (_oki_0_io_rom_addr),
    .io_rom_dout    (_oki_0_io_rom_dout),
    .io_rom_valid   (_oki_0_io_rom_valid),
    .io_audio_valid (_oki_0_io_audio_valid),
    .io_audio_bits  (_oki_0_io_audio_bits)
  );
  OKIM6295_1 oki_1 (
    .clock          (clock),
    .reset          (reset),
    .io_cpu_wr      (oki_1_io_cpu_wr),
    .io_cpu_din     (oki_1_io_cpu_din),
    .io_cpu_dout    (_oki_1_io_cpu_dout),
    .io_rom_addr    (_oki_1_io_rom_addr),
    .io_rom_dout    (io_rom_1_dout),
    .io_rom_valid   (io_rom_1_valid),
    .io_audio_valid (_oki_1_io_audio_valid),
    .io_audio_bits  (_oki_1_io_audio_bits)
  );
  assign _ymz280b_io_cpu_addr = io_ctrl_ymz_addr[0];
  assign _ymz280b_io_cpu_din = io_ctrl_ymz_din[7:0];
  YMZ280B ymz280b (
    .clock              (clock),
    .reset              (reset),
    .io_cpu_rd          (io_ctrl_ymz_rd),
    .io_cpu_wr          (io_ctrl_ymz_wr),
    .io_cpu_addr        (_ymz280b_io_cpu_addr),
    .io_cpu_din         (_ymz280b_io_cpu_din),
    .io_cpu_dout        (_ymz280b_io_cpu_dout),
    .io_rom_rd          (_ymz280b_io_rom_rd),
    .io_rom_addr        (_ymz280b_io_rom_addr),
    .io_rom_dout        (_ymz280b_io_rom_dout),
    .io_rom_wait_n      (_ymz280b_io_rom_wait_n),
    .io_rom_valid       (_ymz280b_io_rom_valid),
    .io_audio_valid     (_ymz280b_io_audio_valid),
    .io_audio_bits_left (_ymz280b_io_audio_bits_left),
    .io_irq             (io_ctrl_irq)
  );
  assign _ym2203_io_cpu_wr = _GEN & _ym2203_io_cpu_wr_T & _cpu_io_wr;
  assign _ym2203_io_cpu_addr = _cpu_io_addr[0];
  YM2203 ym2203 (
    .clock             (clock),
    .reset             (reset),
    .io_cpu_wr         (_ym2203_io_cpu_wr),
    .io_cpu_addr       (_ym2203_io_cpu_addr),
    .io_cpu_din        (_cpu_io_dout),
    .io_cpu_dout       (_ym2203_io_cpu_dout),
    .io_irq            (_cpu_io_int),
    .io_audio_valid    (_ym2203_io_audio_valid),
    .io_audio_bits_psg (_ym2203_io_audio_bits_psg),
    .io_audio_bits_fm  (_ym2203_io_audio_bits_fm)
  );
  assign _arbiter_io_in_0_rd = io_gameConfig_sound_0_device == 2'h2;
  assign _arbiter_io_in_1_rd = io_gameConfig_sound_0_device == 2'h1 & _ymz280b_io_rom_rd;
  assign _arbiter_io_in_1_addr = {1'h0, _ymz280b_io_rom_addr};
  assign _arbiter_io_in_2_rd =
    (&io_gameConfig_sound_0_device) & _GEN & cs & ~_cpu_io_rfsh;
  assign _arbiter_io_in_2_addr = {9'h0, _cpu_io_addr};
  assign _arbiter_io_in_3_rd =
    (&io_gameConfig_sound_0_device) & _GEN & cs_1 & ~_cpu_io_rfsh;
  assign _arbiter_io_in_3_addr = {7'h0, z80BankReg, _cpu_io_addr[13:0]};
  AsyncReadMemArbiter arbiter (
    .clock          (clock),
    .reset          (reset),
    .io_in_0_rd     (_arbiter_io_in_0_rd),
    .io_in_0_addr   (mem_addr),
    .io_in_0_dout   (_oki_0_io_rom_dout),
    .io_in_0_valid  (_oki_0_io_rom_valid),
    .io_in_1_rd     (_arbiter_io_in_1_rd),
    .io_in_1_addr   (_arbiter_io_in_1_addr),
    .io_in_1_dout   (_ymz280b_io_rom_dout),
    .io_in_1_wait_n (_ymz280b_io_rom_wait_n),
    .io_in_1_valid  (_ymz280b_io_rom_valid),
    .io_in_2_rd     (_arbiter_io_in_2_rd),
    .io_in_2_addr   (_arbiter_io_in_2_addr),
    .io_in_2_dout   (_arbiter_io_in_2_dout),
    .io_in_3_rd     (_arbiter_io_in_3_rd),
    .io_in_3_addr   (_arbiter_io_in_3_addr),
    .io_in_3_dout   (_arbiter_io_in_3_dout),
    .io_out_rd      (io_rom_0_rd),
    .io_out_addr    (io_rom_0_addr),
    .io_out_dout    (io_rom_0_dout),
    .io_out_wait_n  (io_rom_0_wait_n),
    .io_out_valid   (io_rom_0_valid)
  );
  AudioMixer io_audio_mixer (
    .clock   (clock),
    .io_in_4 (io_audio_r_4),
    .io_in_3 (io_audio_r_3),
    .io_in_2 (io_audio_r_2),
    .io_in_1 (io_audio_r_1),
    .io_in_0 (io_audio_r),
    .io_out  (io_audio)
  );
  assign io_ctrl_oki_0_dout = {8'h0, _oki_0_io_cpu_dout};
  assign io_ctrl_oki_1_dout = {8'h0, _oki_1_io_cpu_dout};
  assign io_ctrl_ymz_dout = {8'h0, _ymz280b_io_cpu_dout};
  assign io_rom_1_addr =
    _mem_addr_T_4
      ? _nmk_io_addr_1_out
      : {4'h0, mem_addr_bank_1, _oki_1_io_rom_addr[16:0]};
endmodule

