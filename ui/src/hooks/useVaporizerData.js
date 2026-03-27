import { useEffect } from 'react'
import useStore from '../store/useStore'

export function useVaporizerData() {
  const setTelemetry = useStore((state) => state.setTelemetry)
  const setSocket = useStore((state) => state.setSocket)

  useEffect(() => {
    let socket
    let reconnectTimeout

    const connect = () => {
      socket = new WebSocket('ws://127.0.0.1:8080')

      socket.onopen = () => {
        console.log('Connected to Vaporizer WebSocket')
        setSocket(socket)
      }

      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          setTelemetry(data)
        } catch (e) {
          console.error('Failed to parse telemetry', e)
        }
      }

      socket.onclose = () => {
        console.log('WebSocket closed, reconnecting...')
        setSocket(null)
        // Ensure UI reflects disconnection immediately
        setTelemetry({ connectionState: 'Disconnected', status: null })
        reconnectTimeout = setTimeout(connect, 3000)
      }

      socket.onerror = (err) => {
        console.error('WebSocket error', err)
        socket.close()
      }
    }

    connect()

    return () => {
      if (socket) socket.close()
      if (reconnectTimeout) clearTimeout(reconnectTimeout)
    }
  }, [setTelemetry])
}
