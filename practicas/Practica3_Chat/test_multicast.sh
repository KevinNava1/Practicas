#!/bin/bash

echo "==================================="
echo "🔍 DIAGNÓSTICO MULTICAST EN MACOS"
echo "==================================="

echo ""
echo "📡 Interfaces de red disponibles:"
ifconfig | grep "^[a-z]" | cut -d: -f1

echo ""
echo "🌐 Interfaces con multicast:"
ifconfig | grep -B 3 "MULTICAST"

echo ""
echo "📋 Rutas multicast:"
netstat -rn | grep -E "^(224|239)"

echo ""
echo "🔥 Estado del Firewall:"
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate

echo ""
echo "✅ Interfaz recomendada: en0 (WiFi/Ethernet)"
