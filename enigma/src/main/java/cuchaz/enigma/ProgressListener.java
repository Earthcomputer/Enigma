package cuchaz.enigma;

public interface ProgressListener {
	static ProgressListener none() {
		return new ProgressListener() {
			@Override
			public void init(int totalWork, String title) {
			}

			@Override
			public void step(int numDone, String message) {
			}

			@Override
			public ProgressListener fork() {
				return this;
			}
		};
	}

	void init(int totalWork, String title);

	void step(int numDone, String message);

	ProgressListener fork();
}
