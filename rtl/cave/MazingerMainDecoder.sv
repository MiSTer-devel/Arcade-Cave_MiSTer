// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module MazingerMainDecoder(
  input         game_active,
  input  [22:0] cpu_addr,
  input  [2:0]  cpu_fc,
  input         cpu_as,
  input         cpu_rw,
  input         read_strobe,
  input         write_strobe,
  input         prog_rom_valid,
  input         dtack_reg,
  output [23:0] cpu_byte_addr,
  output        prog_rom_select,
  output        main_ram_select,
  output        sprite_ram_select,
  output        video_regs_select,
  output        irq_select,
  output        sprite_regs_select,
  output        sprite_swap_write,
  output        sound_select,
  output        sound_read,
  output        sound_write,
  output        layer1_vram8_select,
  output        layer0_vram8_select,
  output        layer1_regs_select,
  output        layer0_regs_select,
  output        input0_select,
  output        input1_select,
  output        input0_cycle,
  output        input1_cycle,
  output        input0_read,
  output        input1_read,
  output        eeprom_select,
  output        eeprom_cycle,
  output        eeprom_write,
  output        palette_select,
  output [14:0] palette_ram_addr,
  output        extra_rom_select,
  output        no_op_select,
  output        prog_rom_access,
  output        prog_rom_ready,
  output        irq_read,
  output [1:0]  irq_word_offset,
  output        video_irq_clear,
  output        unknown_irq_clear,
  output        sync_dtack,
  output        cycle,
  output        known_select,
  output        unmapped_cycle,
  output        prog_rom_read,
  output        main_ram_read,
  output        main_ram_write,
  output        sprite_ram_read,
  output        sprite_ram_write,
  output        layer0_vram8_read,
  output        layer1_vram8_read,
  output        layer0_vram8_write,
  output        layer1_vram8_write,
  output        video_regs_read,
  output        video_regs_write,
  output        layer0_regs_read,
  output        layer1_regs_read,
  output        layer0_regs_write,
  output        layer1_regs_write,
  output        palette_read,
  output        palette_write,
  output        sprite_regs_write,
  output        cpu_space,
  output        data_strobe,
  output        open_bus_select,
  output        dtack
);
  assign cpu_byte_addr = {cpu_addr, 1'b0};

  assign prog_rom_select = cpu_byte_addr[23:19] == 5'h00;
  assign main_ram_select = cpu_byte_addr[23:16] == 8'h10;
  assign sprite_ram_select = cpu_byte_addr[23:16] == 8'h20;
  assign video_regs_select = cpu_byte_addr[23:7] == 17'h6000;
  assign irq_select = cpu_byte_addr[23:3] == 21'h60000;
  assign sprite_regs_select = cpu_byte_addr[23:4] == 20'h30000;
  assign sprite_swap_write = (cpu_addr == 23'h180004) & write_strobe;
  assign sound_select = cpu_addr == 23'h180037;
  assign sound_read = sound_select & read_strobe;
  assign sound_write = sound_select & write_strobe;
  assign layer1_vram8_select = cpu_byte_addr[23:16] == 8'h40;
  assign layer0_vram8_select = cpu_byte_addr[23:16] == 8'h50;
  assign layer1_regs_select =
    (cpu_byte_addr[23:4] == 20'h60000) & (cpu_byte_addr[3:1] < 3'h3);
  assign layer0_regs_select =
    (cpu_byte_addr[23:4] == 20'h70000) & (cpu_byte_addr[3:1] < 3'h3);
  assign input0_select = cpu_addr == 23'h400000;
  assign input1_select = cpu_addr == 23'h400001;
  assign input0_cycle = input0_select & cpu_rw;
  assign input1_cycle = input1_select & cpu_rw;
  assign input0_read = input0_select & read_strobe;
  assign input1_read = input1_select & read_strobe;
  assign eeprom_select = cpu_addr == 23'h480000;
  assign eeprom_cycle = eeprom_select & ~cpu_rw;
  assign eeprom_write = eeprom_select & write_strobe;
  assign palette_select = cpu_byte_addr[23:15] == 9'h181;
  assign palette_ram_addr = cpu_addr[14:0] - 15'h4000;
  assign extra_rom_select = cpu_byte_addr[23:19] == 5'h1a;
  assign no_op_select = (cpu_byte_addr > 24'h10ffff) & (cpu_byte_addr < 24'h200000);
  assign prog_rom_access = prog_rom_select | extra_rom_select;
  assign prog_rom_ready = prog_rom_access & cpu_rw & prog_rom_valid;
  assign irq_read = irq_select & read_strobe;
  assign irq_word_offset = cpu_addr[1:0];
  assign video_irq_clear = irq_read & (irq_word_offset == 2'h2);
  assign unknown_irq_clear = irq_read & (irq_word_offset == 2'h3);

  assign sync_dtack =
    main_ram_select | sprite_ram_select | video_regs_select | layer1_vram8_select
    | layer0_vram8_select | layer1_regs_select | layer0_regs_select | input0_read
    | input1_read | eeprom_write | palette_select | no_op_select;
  assign cycle = game_active & cpu_as;
  assign known_select =
    prog_rom_access | main_ram_select | sprite_ram_select | video_regs_select
    | layer1_vram8_select | layer0_vram8_select | layer1_regs_select
    | layer0_regs_select | input0_cycle | input1_cycle | eeprom_cycle | palette_select
    | no_op_select | sound_select | irq_select | sprite_regs_select;
  assign unmapped_cycle = cycle & ~known_select;

  assign prog_rom_read = prog_rom_access & read_strobe;
  assign main_ram_read = main_ram_select & read_strobe;
  assign main_ram_write = main_ram_select & write_strobe;
  assign sprite_ram_read = sprite_ram_select & read_strobe;
  assign sprite_ram_write = sprite_ram_select & write_strobe;
  assign layer0_vram8_read = layer0_vram8_select & read_strobe;
  assign layer1_vram8_read = layer1_vram8_select & read_strobe;
  assign layer0_vram8_write = layer0_vram8_select & write_strobe;
  assign layer1_vram8_write = layer1_vram8_select & write_strobe;
  assign video_regs_read = video_regs_select & read_strobe;
  assign video_regs_write = video_regs_select & write_strobe;
  assign layer0_regs_read = layer0_regs_select & read_strobe;
  assign layer1_regs_read = layer1_regs_select & read_strobe;
  assign layer0_regs_write = layer0_regs_select & write_strobe;
  assign layer1_regs_write = layer1_regs_select & write_strobe;
  assign palette_read = palette_select & read_strobe;
  assign palette_write = palette_select & write_strobe;
  assign sprite_regs_write = sprite_regs_select & write_strobe;

  assign cpu_space = &cpu_fc;
  assign data_strobe = read_strobe | write_strobe;
  assign open_bus_select = unmapped_cycle & ~cpu_space & data_strobe;
  assign dtack = cpu_as & (prog_rom_ready | sync_dtack | open_bus_select | dtack_reg);
endmodule
