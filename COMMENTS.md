# Comments:

## Assumptions:

1. Assuming that data pkt len and window_size is also provided to the Recvr.
2. Time printed is since start of the program.
3. Assume ack pkt len is 40.
4. MicroSeconds and Milliseconds format xxx:yyy
5. Assuming instantaneous pkt generation
6. No Out of order ACKs
7. -dd cmd line option for deep debug

## Issues faced/Experience with Project:

1. Synchronization issues for shared resources among threads. Had to figure out correct locking points and aggregate common resources.
2. Usage of Timers - granularity of timers in Java. Could not go beyong millisecond level timers.

## Suggestions:

1. NA
