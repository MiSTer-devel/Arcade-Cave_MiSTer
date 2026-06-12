local out_path = os.getenv("MAME_PWRINST2_SOUND_TRACE_OUT") or "pwrinst2_sound_trace.csv"
local trace_seconds = tonumber(os.getenv("MAME_PWRINST2_SOUND_TRACE_SECONDS") or "18.0") or 18.0
local inject_start_frame = tonumber(os.getenv("MAME_PWRINST2_INJECT_START_FRAME") or "90") or 90
local inject_spacing_frames = tonumber(os.getenv("MAME_PWRINST2_INJECT_SPACING_FRAMES") or "30") or 30
local out = assert(io.open(out_path, "w"))
out:setvbuf("line")

local machine = manager.machine
local maincpu = assert(machine.devices[":maincpu"], "missing :maincpu")
local audiocpu = assert(machine.devices[":audiocpu"], "missing :audiocpu")
local main_space = assert(maincpu.spaces["program"], "missing main program space")
local io_space = assert(audiocpu.spaces["io"], "missing audiocpu io space")

local frame = 0
local ym_reg = 0
local oki_pending = { false, false }
local oki_phrase = { 0, 0 }
local oki_events = { 0, 0 }
local oki_last_starts = { 0, 0 }
local oki_last_stops = { 0, 0 }
local nmk_pages = { 0, 0, 0, 0, 0, 0, 0, 0 }
local last_main_command = 0
local last_reply = 0
local ym_writes = 0
local z80_reply_writes = 0
local last_z80_reply = -1
local inject_commands = {}
local inject_index = 1
local inject_next_frame = inject_start_frame

local inject_env = os.getenv("MAME_PWRINST2_INJECT_COMMANDS")
if inject_env then
  for token in string.gmatch(inject_env, "([^,]+)") do
    token = token:gsub("^%s+", ""):gsub("%s+$", "")
    local value
    if token:sub(1, 2) == "0x" or token:sub(1, 2) == "0X" then
      value = tonumber(token:sub(3), 16)
    else
      value = tonumber(token)
    end
    if value then
      inject_commands[#inject_commands + 1] = value & 0xffff
    end
  end
end

local function hx(value, digits)
  return string.format("%0" .. digits .. "X", value & ((1 << (digits * 4)) - 1))
end

local function now()
  return machine.time:as_double()
end

local function main_pc()
  return maincpu.state["PC"].value & 0xffffff
end

local function z80_pc()
  return audiocpu.state["PC"].value & 0xffff
end

local function log(kind, detail, addr, data, mask)
  out:write(string.format(
    "%.6f,%d,%s,%s,%s,%s,%s,%s\n",
    now(),
    frame,
    kind,
    detail,
    hx(addr or 0, 6),
    hx(data or 0, 4),
    hx(mask or 0, 4),
    hx(z80_pc(), 4)))
end

local function log_main(kind, detail, addr, data, mask)
  out:write(string.format(
    "%.6f,%d,%s,%s,%s,%s,%s,mainpc_%s\n",
    now(),
    frame,
    kind,
    detail,
    hx(addr or 0, 6),
    hx(data or 0, 4),
    hx(mask or 0, 4),
    hx(main_pc(), 6)))
end

local function drain_main_replies(limit)
  for i = 1, limit do
    local reply = main_space:read_u16(0xd80000) & 0xffff
    log_main("LUA", "drain_sound_ack_" .. tostring(i), 0xd80000, reply, 0xffff)
    if (reply & 0xff) == 0xff then
      return
    end
  end
end

local function trace_oki(chip, value)
  local index = chip + 1
  value = value & 0xff
  oki_events[index] = oki_events[index] + 1

  if value >= 0x80 then
    oki_pending[index] = true
    oki_phrase[index] = value & 0x7f
    log("OKI" .. chip, "phrase_" .. hx(value & 0x7f, 2), chip == 0 and 0 or 8, value, 0xff)
  elseif oki_pending[index] then
    oki_pending[index] = false
    oki_last_starts[index] = value
    log("OKI" .. chip, "start_phrase_" .. hx(oki_phrase[index], 2) .. "_chan_" .. hx((value >> 4) & 0xf, 1) .. "_att_" .. hx(value & 0xf, 1), chip == 0 and 0 or 8, value, 0xff)
  else
    oki_last_stops[index] = value
    log("OKI" .. chip, "stop_" .. hx((value >> 3) & 0xf, 1), chip == 0 and 0 or 8, value, 0xff)
  end
end

out:write("time,frame,kind,detail,addr,data,mask,pc\n")
log("TRACE", "start", 0, 0, 0)

emu.register_frame_done(function()
  frame = frame + 1
  if inject_index <= #inject_commands and frame >= inject_next_frame then
    local command = inject_commands[inject_index]
    main_space:write_u16(0xe00000, command)
    log_main("LUA", "inject_sound_command_" .. hx(command, 4), 0xe00000, command, 0xffff)
    drain_main_replies(8)
    inject_index = inject_index + 1
    inject_next_frame = inject_next_frame + inject_spacing_frames
  end
end, "pwrinst2_sound_trace_frame")

main_space:install_write_tap(0xe00000, 0xe00001, "pwrinst2_main_sound_cmd_w", function(offset, data, mask)
  last_main_command = data & 0xffff
  log_main("M68K", "sound_command", offset, data, mask)
end)

main_space:install_read_tap(0xe00002, 0xe00003, "pwrinst2_main_sound_ack_r", function(offset, data, mask)
  log_main("M68K", "sound_ack", offset, data, mask)
end)

io_space:install_write_tap(0x00, 0x00, "pwrinst2_oki0_w", function(offset, data, mask)
  trace_oki(0, data)
end)

io_space:install_write_tap(0x08, 0x08, "pwrinst2_oki1_w", function(offset, data, mask)
  trace_oki(1, data)
end)

io_space:install_write_tap(0x10, 0x17, "pwrinst2_nmk112_w", function(offset, data, mask)
  local bank = (offset - 0x10) & 7
  nmk_pages[bank + 1] = data & 0xff
  log("NMK112", "bank_" .. hx(bank, 1), offset, data, mask)
end)

io_space:install_write_tap(0x40, 0x41, "pwrinst2_ym2203_w", function(offset, data, mask)
  ym_writes = ym_writes + 1
  if offset == 0x40 then
    ym_reg = data & 0xff
  else
    local reg = ym_reg & 0xff
    if reg >= 0x28 and reg <= 0x2f then
      log("YM2203", "key_data_reg_" .. hx(reg, 2), offset, data, mask)
    end
  end
end)

io_space:install_write_tap(0x50, 0x50, "pwrinst2_reply_w", function(offset, data, mask)
  last_reply = data & 0xff
  z80_reply_writes = z80_reply_writes + 1
  if z80_reply_writes <= 32 or last_reply ~= last_z80_reply then
    log("Z80", "reply_ack", offset, data, mask)
  end
  last_z80_reply = last_reply
end)

io_space:install_read_tap(0x60, 0x70, "pwrinst2_latch_r", function(offset, data, mask)
  if offset == 0x60 or offset == 0x70 then
    log("Z80", offset == 0x60 and "latch_hi" or "latch_lo", offset, data, mask)
  end
end)

io_space:install_write_tap(0x80, 0x80, "pwrinst2_z80_bank_w", function(offset, data, mask)
  log("Z80", "rom_bank", offset, data, mask)
end)

emu.wait(trace_seconds)

log("TRACE", "summary_main_" .. hx(last_main_command, 4) .. "_reply_" .. hx(last_reply, 2), 0, 0, 0)
log("TRACE", "summary_oki0_events_" .. tostring(oki_events[1]) .. "_last_start_" .. hx(oki_last_starts[1], 2) .. "_last_stop_" .. hx(oki_last_stops[1], 2), 0, 0, 0)
log("TRACE", "summary_oki1_events_" .. tostring(oki_events[2]) .. "_last_start_" .. hx(oki_last_starts[2], 2) .. "_last_stop_" .. hx(oki_last_stops[2], 2), 0, 0, 0)
log("TRACE", "summary_ym_writes_" .. tostring(ym_writes), 0, 0, 0)
log("TRACE", "summary_z80_reply_writes_" .. tostring(z80_reply_writes), 0, 0, 0)
log("TRACE", "summary_injected_" .. tostring(inject_index - 1), 0, 0, 0)
log("TRACE", "done", 0, 0, 0)
out:close()
machine:exit()
