local out_path = os.getenv("MAME_PWRINST2_NMK112_PROBE_OUT") or "pwrinst2_nmk112_probe.txt"
local out = assert(io.open(out_path, "w"))
out:setvbuf("line")

local machine = manager.machine
local audiocpu = assert(machine.devices[":audiocpu"], "missing :audiocpu")
local io_space = assert(audiocpu.spaces["io"], "missing audiocpu io space")
local oki1 = assert(machine.devices[":oki1"].spaces["rom"], "missing :oki1 rom space")
local oki2 = assert(machine.devices[":oki2"].spaces["rom"], "missing :oki2 rom space")

local function hx(value, digits)
  return string.format("%0" .. digits .. "X", value & ((1 << (digits * 4)) - 1))
end

local probe_addrs = { 0x00000, 0x00100, 0x00200, 0x00300, 0x00400, 0x10000, 0x20000, 0x30000 }

local function read_space(label, space)
  for _, addr in ipairs(probe_addrs) do
    out:write(string.format("%s addr=%05X data=%02X\n", label, addr, space:read_u8(addr) & 0xff))
  end
end

local function write_bank(offset, data)
  io_space:write_u8(0x10 + offset, data & 0xff)
  out:write(string.format("write offset=%X data=%02X\n", offset, data & 0xff))
end

out:write("initial boot mapping\n")
read_space("oki1", oki1)
read_space("oki2", oki2)

out:write("set both chips banks 0,1,2,3 like pwrinst2 init\n")
write_bank(0, 0x00)
write_bank(4, 0x00)
write_bank(1, 0x01)
write_bank(5, 0x01)
write_bank(2, 0x02)
write_bank(6, 0x02)
write_bank(3, 0x03)
write_bank(7, 0x03)
read_space("oki1", oki1)
read_space("oki2", oki2)

out:write("probe high page on chip 0 bank 0\n")
write_bank(0, 0x10)
read_space("oki1", oki1)
write_bank(0, 0x20)
read_space("oki1", oki1)
write_bank(0, 0x3f)
read_space("oki1", oki1)

out:write("probe high page on chip 1 bank 0\n")
write_bank(4, 0x10)
read_space("oki2", oki2)
write_bank(4, 0x20)
read_space("oki2", oki2)
write_bank(4, 0x3f)
read_space("oki2", oki2)

out:close()
machine:exit()
