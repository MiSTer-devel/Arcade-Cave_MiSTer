// Yamaha YMZ280B register front-end and channel-controller wrapper.
module YMZ280B(
  input         clock,
  input         reset,
  input         io_cpu_rd,
  input         io_cpu_wr,
  input         io_cpu_addr,
  input  [7:0]  io_cpu_din,
  output [7:0]  io_cpu_dout,
  output        io_rom_rd,
  output [23:0] io_rom_addr,
  input  [7:0]  io_rom_dout,
  input         io_rom_wait_n,
  input         io_rom_valid,
  output        io_audio_valid,
  output [15:0] io_audio_bits_left,
  output        io_irq
);
  reg [7:0] addrReg;
  reg [7:0] dataReg;
  reg [7:0] statusReg;
  reg [7:0] registerFile [0:127];
  reg [7:0] irqMaskReg;
  reg [7:0] controlReg;

  wire        writeAddr = io_cpu_wr & ~io_cpu_addr;
  wire        writeData = io_cpu_wr & io_cpu_addr;
  wire        readStatus = io_cpu_rd & io_cpu_addr;

  wire        channelDone;
  wire [2:0] channelIndex;

  integer i;
  always @(posedge clock) begin
    if (reset) begin
      addrReg <= 8'h00;
      dataReg <= 8'h00;
      statusReg <= 8'h00;
      irqMaskReg <= 8'h00;
      controlReg <= 8'h00;

      for (i = 0; i < 128; i = i + 1)
        registerFile[i] <= 8'h00;
    end
    else begin
      if (writeAddr)
        addrReg <= io_cpu_din;

      if (readStatus)
        dataReg <= statusReg;

      if (readStatus)
        statusReg <= 8'h00;
      else if (channelDone)
        statusReg <= statusReg | (8'h01 << channelIndex);

      if (writeData) begin
        if (~addrReg[7])
          registerFile[addrReg[6:0]] <= io_cpu_din;
        else if (addrReg == 8'hFE)
          irqMaskReg <= io_cpu_din;
        else if (addrReg == 8'hFF)
          controlReg <= io_cpu_din;
      end
    end
  end

  ChannelController channelCtrl (
    .clock                   (clock),
    .reset                   (reset),

    .io_regs_0_pitch         (registerFile[7'h00]),
    .io_regs_0_flags_keyOn   (registerFile[7'h01][7]),
    .io_regs_0_flags_loop    (registerFile[7'h01][4]),
    .io_regs_0_level         (registerFile[7'h02]),
    .io_regs_0_pan           (registerFile[7'h03][3:0]),
    .io_regs_0_startAddr     ({registerFile[7'h20], registerFile[7'h40], registerFile[7'h60]}),
    .io_regs_0_loopStartAddr ({registerFile[7'h21], registerFile[7'h41], registerFile[7'h61]}),
    .io_regs_0_loopEndAddr   ({registerFile[7'h22], registerFile[7'h42], registerFile[7'h62]}),
    .io_regs_0_endAddr       ({registerFile[7'h23], registerFile[7'h43], registerFile[7'h63]}),

    .io_regs_1_pitch         (registerFile[7'h04]),
    .io_regs_1_flags_keyOn   (registerFile[7'h05][7]),
    .io_regs_1_flags_loop    (registerFile[7'h05][4]),
    .io_regs_1_level         (registerFile[7'h06]),
    .io_regs_1_pan           (registerFile[7'h07][3:0]),
    .io_regs_1_startAddr     ({registerFile[7'h24], registerFile[7'h44], registerFile[7'h64]}),
    .io_regs_1_loopStartAddr ({registerFile[7'h25], registerFile[7'h45], registerFile[7'h65]}),
    .io_regs_1_loopEndAddr   ({registerFile[7'h26], registerFile[7'h46], registerFile[7'h66]}),
    .io_regs_1_endAddr       ({registerFile[7'h27], registerFile[7'h47], registerFile[7'h67]}),

    .io_regs_2_pitch         (registerFile[7'h08]),
    .io_regs_2_flags_keyOn   (registerFile[7'h09][7]),
    .io_regs_2_flags_loop    (registerFile[7'h09][4]),
    .io_regs_2_level         (registerFile[7'h0A]),
    .io_regs_2_pan           (registerFile[7'h0B][3:0]),
    .io_regs_2_startAddr     ({registerFile[7'h28], registerFile[7'h48], registerFile[7'h68]}),
    .io_regs_2_loopStartAddr ({registerFile[7'h29], registerFile[7'h49], registerFile[7'h69]}),
    .io_regs_2_loopEndAddr   ({registerFile[7'h2A], registerFile[7'h4A], registerFile[7'h6A]}),
    .io_regs_2_endAddr       ({registerFile[7'h2B], registerFile[7'h4B], registerFile[7'h6B]}),

    .io_regs_3_pitch         (registerFile[7'h0C]),
    .io_regs_3_flags_keyOn   (registerFile[7'h0D][7]),
    .io_regs_3_flags_loop    (registerFile[7'h0D][4]),
    .io_regs_3_level         (registerFile[7'h0E]),
    .io_regs_3_pan           (registerFile[7'h0F][3:0]),
    .io_regs_3_startAddr     ({registerFile[7'h2C], registerFile[7'h4C], registerFile[7'h6C]}),
    .io_regs_3_loopStartAddr ({registerFile[7'h2D], registerFile[7'h4D], registerFile[7'h6D]}),
    .io_regs_3_loopEndAddr   ({registerFile[7'h2E], registerFile[7'h4E], registerFile[7'h6E]}),
    .io_regs_3_endAddr       ({registerFile[7'h2F], registerFile[7'h4F], registerFile[7'h6F]}),

    .io_regs_4_pitch         (registerFile[7'h10]),
    .io_regs_4_flags_keyOn   (registerFile[7'h11][7]),
    .io_regs_4_flags_loop    (registerFile[7'h11][4]),
    .io_regs_4_level         (registerFile[7'h12]),
    .io_regs_4_pan           (registerFile[7'h13][3:0]),
    .io_regs_4_startAddr     ({registerFile[7'h30], registerFile[7'h50], registerFile[7'h70]}),
    .io_regs_4_loopStartAddr ({registerFile[7'h31], registerFile[7'h51], registerFile[7'h71]}),
    .io_regs_4_loopEndAddr   ({registerFile[7'h32], registerFile[7'h52], registerFile[7'h72]}),
    .io_regs_4_endAddr       ({registerFile[7'h33], registerFile[7'h53], registerFile[7'h73]}),

    .io_regs_5_pitch         (registerFile[7'h14]),
    .io_regs_5_flags_keyOn   (registerFile[7'h15][7]),
    .io_regs_5_flags_loop    (registerFile[7'h15][4]),
    .io_regs_5_level         (registerFile[7'h16]),
    .io_regs_5_pan           (registerFile[7'h17][3:0]),
    .io_regs_5_startAddr     ({registerFile[7'h34], registerFile[7'h54], registerFile[7'h74]}),
    .io_regs_5_loopStartAddr ({registerFile[7'h35], registerFile[7'h55], registerFile[7'h75]}),
    .io_regs_5_loopEndAddr   ({registerFile[7'h36], registerFile[7'h56], registerFile[7'h76]}),
    .io_regs_5_endAddr       ({registerFile[7'h37], registerFile[7'h57], registerFile[7'h77]}),

    .io_regs_6_pitch         (registerFile[7'h18]),
    .io_regs_6_flags_keyOn   (registerFile[7'h19][7]),
    .io_regs_6_flags_loop    (registerFile[7'h19][4]),
    .io_regs_6_level         (registerFile[7'h1A]),
    .io_regs_6_pan           (registerFile[7'h1B][3:0]),
    .io_regs_6_startAddr     ({registerFile[7'h38], registerFile[7'h58], registerFile[7'h78]}),
    .io_regs_6_loopStartAddr ({registerFile[7'h39], registerFile[7'h59], registerFile[7'h79]}),
    .io_regs_6_loopEndAddr   ({registerFile[7'h3A], registerFile[7'h5A], registerFile[7'h7A]}),
    .io_regs_6_endAddr       ({registerFile[7'h3B], registerFile[7'h5B], registerFile[7'h7B]}),

    .io_regs_7_pitch         (registerFile[7'h1C]),
    .io_regs_7_flags_keyOn   (registerFile[7'h1D][7]),
    .io_regs_7_flags_loop    (registerFile[7'h1D][4]),
    .io_regs_7_level         (registerFile[7'h1E]),
    .io_regs_7_pan           (registerFile[7'h1F][3:0]),
    .io_regs_7_startAddr     ({registerFile[7'h3C], registerFile[7'h5C], registerFile[7'h7C]}),
    .io_regs_7_loopStartAddr ({registerFile[7'h3D], registerFile[7'h5D], registerFile[7'h7D]}),
    .io_regs_7_loopEndAddr   ({registerFile[7'h3E], registerFile[7'h5E], registerFile[7'h7E]}),
    .io_regs_7_endAddr       ({registerFile[7'h3F], registerFile[7'h5F], registerFile[7'h7F]}),

    .io_enable               (controlReg[7]),
    .io_done                 (channelDone),
    .io_index                (channelIndex),
    .io_audio_valid          (io_audio_valid),
    .io_audio_bits_left      (io_audio_bits_left),
    .io_rom_rd               (io_rom_rd),
    .io_rom_addr             (io_rom_addr),
    .io_rom_dout             (io_rom_dout),
    .io_rom_wait_n           (io_rom_wait_n),
    .io_rom_valid            (io_rom_valid)
  );

  assign io_cpu_dout = dataReg;
  assign io_irq = controlReg[4] & (|(statusReg & irqMaskReg));
endmodule
