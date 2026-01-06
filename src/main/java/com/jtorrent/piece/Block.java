package com.jtorrent.piece;

public record Block(int  pieceIndex, int offset, int length, int blockIndex) {}
