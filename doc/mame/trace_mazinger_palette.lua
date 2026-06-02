local out_path = os.getenv("MAME_MAZINGER_PALETTE_TRACE_OUT") or "mazinger_palette_trace.txt"
local trace_seconds = tonumber(os.getenv("MAME_MAZINGER_PALETTE_TRACE_SECONDS") or "20.0") or 20.0
local out = assert(io.open(out_path, "w"))

local machine = manager.machine
local maincpu = assert(machine.devices[":maincpu"], "missing :maincpu")
local program = assert(maincpu.spaces["program"], "missing main program space")

local write_count = 0
local interesting = {
  [0x0000] = true,
  [0x0001] = true,
  [0x0002] = true,
  [0x0003] = true,
  [0x03f0] = true,
  [0x0400] = true,
  [0x0401] = true,
  [0x07ff] = true,
}

local function hx(value, digits)
  return string.format("%0" .. digits .. "X", value & ((1 << (digits * 4)) - 1))
end

local function now()
  return string.format("%0.6f", machine.time:as_double())
end

local function log(line)
  out:write(now() .. " " .. line .. "\n")
end

local function read_palette_word(offset)
  return program:read_u16(0xc08000 + offset * 2)
end

local function dump_palette(label)
  log(string.format(
    "%s pc=%s pal[0000]=%s pal[0001]=%s pal[0002]=%s pal[0003]=%s pal[03f0]=%s pal[0400]=%s pal[0401]=%s pal[07ff]=%s",
    label,
    hx(maincpu.state["PC"].value, 6),
    hx(read_palette_word(0x0000), 4),
    hx(read_palette_word(0x0001), 4),
    hx(read_palette_word(0x0002), 4),
    hx(read_palette_word(0x0003), 4),
    hx(read_palette_word(0x03f0), 4),
    hx(read_palette_word(0x0400), 4),
    hx(read_palette_word(0x0401), 4),
    hx(read_palette_word(0x07ff), 4)))
end

log("mazinger palette trace start")

program:install_write_tap(0xc08000, 0xc0ffff, "palette_w", function(offset, data, mask)
  local pal_offset = (offset - 0xc08000) >> 1
  local value = data & 0xffff
  write_count = write_count + 1
  if write_count <= 16 or interesting[pal_offset] or ((write_count & 0xfff) == 0) then
    log(string.format(
      "PAL write=%06d off=%04X data=%04X mask=%04X pc=%s",
      write_count,
      pal_offset,
      value,
      mask & 0xffff,
      hx(maincpu.state["PC"].value, 6)))
    out:flush()
  end
end)

emu.wait(trace_seconds)
dump_palette("final")
log(string.format("trace done writes=%d", write_count))
out:close()
machine:exit()
