# Go-Back-N-Simulator
A Simulation for GBN Protocol

## Usage:

1. Make executables using 'make' in the project root directory.
2. For running receiver:
    
    1. Change directory to Code using 'cd Code'   
    2. Run recevier using 'java receiver <options>'
        where options are:
            '-p <port number>' (defaults to 1080) 
            '-d' (defaults to false)
            '-n <max_pkts>' (defaults to 1024)
            '-e <pkt_err_rate>' (defaults to 0.2)
            '-l <pkt_len>' (defaults to 1500)
            '-w <window_size>' (defaults to 8)
            '-dd' (defaults to false)

3. For running sender:

    1. Change directory to Code using 'cd Code'   
    2. Run recevier using 'java sender <options>'
        where options are:
            '-s <hostname>' (defaults to localhost)
            '-p <port number>' (defaults to 1080) 
            '-l <pkt_len>' (defaults to 1500)
            '-r <pkt_gen_rate>' (defaults to 10)
            '-n <max_pkts>' (defaults to 1024)
            '-w <window_size>' (defaults to 8)
            '-b <max_buf_size>' (defaults to 24)
            '-d' (defaults to false)
            '-dd' (defaults to false)
    
 4. Sample option values:
        port = 12346
        hostname = localhost
        pkt_len = 512
        pkt_gen_rate = 10
        max_pkts = 400
        window_size = 5
        max_buf_size = 10
        pkt_err_rate = 0.00001

5. '-d' enables DEBUG mode. '-dd' enables deep debug mode (prints additional information). 