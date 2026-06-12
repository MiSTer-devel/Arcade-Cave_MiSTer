local out_path = os.getenv("MAME_PWRINST2_IOPORT_PROBE_OUT") or "pwrinst2_ioports_probe.txt"
local out = assert(io.open(out_path, "w"))
out:setvbuf("line")

local machine = manager.machine
local ports = machine.ioport.ports

local function write_prop(name, object, prop)
  local ok, value = pcall(function() return object[prop] end)
  if ok and value ~= nil then
    out:write("    " .. name .. "." .. prop .. " = " .. tostring(value) .. "\n")
  end
end

for tag, port in pairs(ports) do
  out:write("port " .. tostring(tag) .. " = " .. tostring(port) .. "\n")
  write_prop("port", port, "tag")
  write_prop("port", port, "value")
  if port.fields ~= nil then
    for key, field in pairs(port.fields) do
      out:write("  field " .. tostring(key) .. " = " .. tostring(field) .. "\n")
      write_prop("field", field, "name")
      write_prop("field", field, "mask")
      write_prop("field", field, "defvalue")
      write_prop("field", field, "type")
      write_prop("field", field, "player")
      write_prop("field", field, "value")
      local mt = getmetatable(field)
      if mt ~= nil then
        out:write("    field.metatable = " .. tostring(mt) .. "\n")
        for mt_key, mt_value in pairs(mt) do
          out:write("      mt." .. tostring(mt_key) .. " = " .. tostring(mt_value) .. "\n")
        end
      end
    end
  end
end

out:close()
machine:exit()
