// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module MazingerMainReadMux(
  input         input1_read,
  input         input0_read,
  input         palette_select,
  input         layer0_regs_select,
  input         layer1_regs_select,
  input         layer0_vram8_select,
  input         layer1_vram8_select,
  input         sound_read,
  input         irq_read,
  input         sprite_ram_select,
  input         boot_ram_select,
  input         main_ram_select,
  input         no_op_read,
  input         prog_rom_ready,
  input         open_bus_read,
  input  [15:0] input1_data,
  input  [15:0] input0_data,
  input  [15:0] palette_data,
  input  [15:0] layer0_regs_data,
  input  [15:0] layer1_regs_data,
  input  [15:0] layer0_vram8_data,
  input  [15:0] layer1_vram8_data,
  input  [15:0] sound_data,
  input  [15:0] irq_data,
  input  [15:0] sprite_ram_data,
  input  [15:0] boot_ram_data,
  input  [15:0] main_ram_data,
  input  [15:0] prog_rom_data,
  output reg    valid,
  output reg [15:0] data
);
  always @* begin
    valid = 1'b1;
    if (input1_read)
      data = input1_data;
    else if (input0_read)
      data = input0_data;
    else if (palette_select)
      data = palette_data;
    else if (layer0_regs_select)
      data = layer0_regs_data;
    else if (layer1_regs_select)
      data = layer1_regs_data;
    else if (layer0_vram8_select)
      data = layer0_vram8_data;
    else if (layer1_vram8_select)
      data = layer1_vram8_data;
    else if (sound_read)
      data = sound_data;
    else if (irq_read)
      data = irq_data;
    else if (sprite_ram_select)
      data = sprite_ram_data;
    else if (boot_ram_select)
      data = boot_ram_data;
    else if (main_ram_select)
      data = main_ram_data;
    else if (no_op_read)
      data = 16'hffff;
    else if (prog_rom_ready)
      data = prog_rom_data;
    else if (open_bus_read)
      data = 16'hffff;
    else begin
      valid = 1'b0;
      data = 16'h0000;
    end
  end
endmodule
