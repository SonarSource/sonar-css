module.exports = {
  collectCoverageFrom: ["src/**/*.ts"],
  // globals: {
  //   "ts-jest": {
  //     tsConfig: "tests/tsconfig.json"
  //   }
  // },
  moduleFileExtensions: ["js", "ts"],
  testResultsProcessor: "jest-sonar-reporter",
  transform: {
    "^.+\\.ts$": "ts-jest"
  },
  testMatch: ["<rootDir>/tests/**/*.test.ts"]
};
