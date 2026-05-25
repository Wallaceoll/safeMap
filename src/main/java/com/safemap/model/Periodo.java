package com.safemap.model;

/**
 * Período do dia em que a ocorrência aconteceu.
 * Calculado automaticamente a partir da hora da dataHora.
 * DIURNO: 06:00 até 17:59 | NOTURNO: 18:00 até 05:59
 */
public enum Periodo {
    DIURNO,
    NOTURNO
}