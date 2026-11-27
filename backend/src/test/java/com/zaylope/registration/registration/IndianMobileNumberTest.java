package com.zaylope.registration.registration;

import com.zaylope.registration.common.exception.InvalidMobileNumberException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndianMobileNumberTest {

	@ParameterizedTest
	@CsvSource({
		"9876543210, +919876543210",
		"' 9876543210 ', +919876543210",
		"+919876543210, +919876543210",
		"+91 9876543210, +919876543210",
		"+91 98765 43210, +919876543210",
		"+91-98765-43210, +919876543210",
		"98765 43210, +919876543210",
		"98765-43210, +919876543210",
		"6000000000, +916000000000"
	})
	void acceptsAndNormalizesValidIndianMobileNumbers(String input, String expected) {
		assertTrue(IndianMobileNumber.isValid(input));
		assertEquals(expected, IndianMobileNumber.normalize(input));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		"   ",
		"12345",
		"1234567890",
		"5999999999",
		"987654321",
		"98765432101",
		"919876543210",
		"98765abc10",
		"98765  43210",
		"98765--43210",
		"+1 9876543210",
		"+91  9876543210",
		"+91- 9876543210",
		"9876543210-",
		"-9876543210",
		"(987) 654-3210",
		"98765\n43210"
	})
	void rejectsNumbersOutsideApprovedIndianFormats(String input) {
		assertFalse(IndianMobileNumber.isValid(input));
		assertThrows(InvalidMobileNumberException.class, () -> IndianMobileNumber.normalize(input));
	}
}
