local out_path = os.getenv("MAME_MAZINGER_SOUND_TRACE_OUT") or "mazinger_sound_trace.txt"
local trace_seconds = tonumber(os.getenv("MAME_MAZINGER_SOUND_TRACE_SECONDS") or "20.0") or 20.0
local out = assert(io.open(out_path, "w"))

local machine = manager.machine
local audiocpu = assert(machine.devices[":audiocpu"], "missing :audiocpu")
local io = assert(audiocpu.spaces["io"], "missing audio I/O space")

local pending_phrase = nil
local write_count = 0

local function hx(value, digits)
  return string.format("%0" .. digits .. "X", value & ((1 << (digits * 4)) - 1))
end

local function now()
  return string.format("%0.6f", machine.time:as_double())
end

local function log(line)
  out:write(now() .. " " .. line .. "\n")
end

log("mazinger sound trace start")

io:install_write_tap(0x00, 0x00, "z80_bank_w", function(offset, data, mask)
  log(string.format("Z80BANK data=%02X pc=%04X", data & 0xff, audiocpu.state["PC"].value & 0xffff))
  out:flush()
end)

io:install_write_tap(0x10, 0x10, "reply_w", function(offset, data, mask)
  log(string.format("REPLY data=%02X pc=%04X", data & 0xff, audiocpu.state["PC"].value & 0xffff))
  out:flush()
end)

io:install_write_tap(0x70, 0x70, "oki_w", function(offset, data, mask)
  local value = data & 0xff
  write_count = write_count + 1

  if pending_phrase ~= nil then
    log(string.format(
      "OKI start phrase=%02X channels=%X atten=%X pc=%04X",
      pending_phrase,
      (value >> 4) & 0x0f,
      value & 0x0f,
      audiocpu.state["PC"].value & 0xffff))
    pending_phrase = nil
  elseif (value & 0x80) ~= 0 then
    pending_phrase = value & 0x7f
    log(string.format("OKI phrase=%02X raw=%02X pc=%04X", pending_phrase, value, audiocpu.state["PC"].value & 0xffff))
  else
    log(string.format("OKI stop channels=%X raw=%02X pc=%04X", (value >> 3) & 0x0f, value, audiocpu.state["PC"].value & 0xffff))
  end

  out:flush()
end)

io:install_write_tap(0x74, 0x74, "oki_bank_w", function(offset, data, mask)
  local value = data & 0xff
  log(string.format(
    "OKIBANK data=%02X lo=%X hi=%X pc=%04X",
    value,
    value & 0x03,
    (value >> 4) & 0x03,
    audiocpu.state["PC"].value & 0xffff))
  out:flush()
end)

emu.wait(trace_seconds)
log(string.format("trace done oki_writes=%d", write_count))
out:close()
machine:exit()
