module.exports = {
  collectCoverageFrom: ["src/**/*.ts"],
  moduleFileExtensions: ["js", "ts"],
  testResultsProcessor: "jest-sonar-reporter",
  transform: {
    "^.+\\.ts$": "ts-jest"
  },
  testMatch: ["<rootDir>/tests/**/*.test.ts"]
};
