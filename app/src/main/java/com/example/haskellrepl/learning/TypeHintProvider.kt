package com.example.haskellrepl.learning

import com.example.haskellrepl.service.ReplOutput

class TypeHintProvider {

	fun extractType(typeOutput: ReplOutput.TypeInfo?): String? {
		return typeOutput?.typeSig
	}
}
