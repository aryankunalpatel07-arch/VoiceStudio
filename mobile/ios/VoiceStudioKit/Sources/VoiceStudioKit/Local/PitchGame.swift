import Foundation

public struct PitchRound: Equatable, Sendable {
    public let first: Double
    public let second: Double
    public var higher: Bool { second > first }
    public init(first: Double, second: Double) { self.first = first; self.second = second }
}

/// Ear-training game shared with Android (`PitchGame.kt`): two tones, is the second
/// higher or lower? Needs no model, network or microphone. `random` yields [0, 1).
public struct PitchGame {
    public static let startingLives = 3
    public static let sampleRate = 44100
    private static let lowest = 220.0
    private static let toneSeconds = 0.45
    private static let pauseSeconds = 0.2
    private static let fadeSeconds = 0.01

    public private(set) var score = 0
    public private(set) var lives = PitchGame.startingLives
    public private(set) var round: PitchRound
    public var over: Bool { lives == 0 }
    public var cents: Double { Self.cents(score) }
    private let random: () -> Double

    public init(random: @escaping () -> Double = { Double.random(in: 0..<1) }) {
        self.random = random
        round = Self.next(score: 0, random: random)
    }

    /// A miss costs a life and keeps the gap; every answer draws fresh tones.
    @discardableResult public mutating func answer(higher: Bool) -> Bool {
        precondition(!over)
        let correct = higher == round.higher
        if correct { score += 1 } else { lives -= 1 }
        if !over { round = Self.next(score: score, random: random) }
        return correct
    }

    /// Starts at a major third and narrows 15% per right answer, never below 10 cents.
    public static func cents(_ score: Int) -> Double { max(10, 400 * pow(0.85, Double(score))) }

    private static func next(score: Int, random: () -> Double) -> PitchRound {
        let first = lowest * pow(2, min(max(random(), 0), 1))
        let up = random() >= 0.5
        return PitchRound(first: first, second: first * pow(2, (up ? 1 : -1) * cents(score) / 1200))
    }

    /// Both tones as mono PCM16 with a short silence between; faded to avoid clicks.
    public static func samples(_ round: PitchRound, sampleRate: Int = PitchGame.sampleRate) -> [Int16] {
        let tone = Int((toneSeconds * Double(sampleRate)).rounded())
        let pause = Int((pauseSeconds * Double(sampleRate)).rounded())
        let fade = Double(Int((fadeSeconds * Double(sampleRate)).rounded()))
        var output = [Int16](repeating: 0, count: tone * 2 + pause)
        for (start, frequency) in [(0, round.first), (tone + pause, round.second)] {
            for index in 0..<tone {
                let envelope = min(1, Double(index) / fade, Double(tone - 1 - index) / fade)
                let value = sin(2 * Double.pi * frequency * Double(index) / Double(sampleRate)) * envelope * 0.5 * Double(Int16.max)
                output[start + index] = Int16(value.rounded())
            }
        }
        return output
    }

    /// The round as a playable WAV file.
    public static func wave(_ round: PitchRound, sampleRate: Int = PitchGame.sampleRate) -> Data {
        let samples = Self.samples(round, sampleRate: sampleRate)
        var data = Data(capacity: 44 + samples.count * 2)
        func append<T: FixedWidthInteger>(_ value: T) { withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) } }
        data.append(contentsOf: Array("RIFF".utf8)); append(UInt32(36 + samples.count * 2))
        data.append(contentsOf: Array("WAVEfmt ".utf8)); append(UInt32(16)); append(UInt16(1)); append(UInt16(1))
        append(UInt32(sampleRate)); append(UInt32(sampleRate * 2)); append(UInt16(2)); append(UInt16(16))
        data.append(contentsOf: Array("data".utf8)); append(UInt32(samples.count * 2))
        for sample in samples { append(sample) }
        return data
    }
}
