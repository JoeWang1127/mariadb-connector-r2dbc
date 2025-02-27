# Change Log

## [1.1.2](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/1.1.2) (12 Mai 2022)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/1.1.1...1.1.2)

* [R2DBC-54] Support r2dbc spec 0.9.1 version
* [R2DBC-42] Specification precision on Statement::add
* [R2DBC-44] simplify client side prepared statement		
* [R2DBC-45] Implement SPI TestKit to validate driver with spec tests		
* [R2DBC-46] Add sql to R2DBC exception hierarchy		
* [R2DBC-47] ensure driver follow spec precision about Row.get returning error.		
* [R2DBC-48] after spec batch clarification trailing batch should fail		
* [R2DBC-49] Support for failover and load balancing modes		
* [R2DBC-50] TIME data without indication default to return Duration in place of LocalTime		
* [R2DBC-56] Transaction isolation spec precision		
* [R2DBC-57] varbinary data default must return byte[]		
* [R2DBC-63] backpressure handling		
* [R2DBC-64] Support batch cancellation		
* [R2DBC-53] correct RowMetadata case-sensitivity lookup		
* [R2DBC-62] Prepared statement wrong column type on prepare meta not skipped

## [1.1.1-rc](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/1.1.1) (13 Sept 2021)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/1.1.0...1.1.1)

Changes:
* [R2DBC-37] Full java 9 JPMS module
* [R2DBC-38] Permit sharing channels with option loopResources

Corrections:
* [R2DBC-40] netty buffer leaks when not consuming results
* [R2DBC-39] MariadbResult.getRowsUpdated() fails with ClassCastException for RETURNING command

## [1.0.3](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/1.0.3) (13 Sept 2021)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/1.0.2...1.0.3)

Corrections:
* [R2DBC-40] netty buffer leaks when not consuming results
* [R2DBC-39] MariadbResult.getRowsUpdated() fails with ClassCastException for RETURNING command

## [1.1.0-beta](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/1.1.0-beta) (15 Jul 2021)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/1.0.2...1.1.0-beta)

Changes:
* [R2DBC-10] - support 10.6 new feature metadata skip
* [R2DBC-21] - Failover capabilities for Connector/R2DBC
* [R2DBC-23] - Restrict authentication plugin list by option  (new option `restrictedAuth`)
* support SPI 0.9 M2
  * [R2DBC-32] - Add support for improved bind parameter declarations
  * [R2DBC-33] - Add Connection.beginTransaction(TransactionDefinition)
  * [R2DBC-34] - implement NoSuchOptionException
  * [R2DBC-35] - Refinement of RowMetadata
  * [R2DBC-36] - Implement statement timeout
  
## [1.0.2](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/1.0.2) (02 Jul 2021)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/1.0.1...1.0.2)

Corrections:
* [R2DBC-24] columns of type Bit(1)/TINYINT(1) now convert as Boolean (new option `tinyInt1isBit`)
* [R2DBC-25] Statement::add correction after specification precision
* [R2DBC-26] handle error like 'too many connection" on socket creation
* [R2DBC-27] Options not parsed from connection string
* [R2DBC-28] mutual authentication not done when using ssl TRUST option
* [R2DBC-29] improve coverage to reaching 90%
* [R2DBC-30] Native Password plugin error

## [1.0.1](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/1.0.1) (09 Mar 2021)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/1.0.0...1.0.1)

Changes:
* [R2DBC-16] Ensure connection autocommit initialisation and new option autocommit 

Corrections:  
* [R2DBC-17] Transactions in query flux might not be persisted
* [R2DBC-19] Data bigger than 16Mb correction.

## [1.0.0](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/1.0.0) (08 Dec 2020)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/0.8.4...1.0.0)

First GA release.

Corrections:
* [R2DBC-14] correcting backpressure handling #7
* [R2DBC-12] Ensure retaining blob buffer #6
* [R2DBC-11] Batching on statement use parameters not added #8
* Rely on 0.8.3 specification (was 0.8.2)
* bump dependencies
* Ensuring row buffer reading position

## [0.8.4](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/0.8.4) (29 Sep 2020)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/0.8.3...0.8.4)

First Release Candidate release.

Changes compared to 0.8.3.beta1:
[R2DBC-9] Non pipelining prepare is close 2 time when race condition cache
[R2DBC-8] synchronous close
[R2DBC-7] authentication error when using multiple classloader
[R2DBC-6] socket option configuration addition for socket Timeout, keep alive and force RST

## [0.8.3](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/0.8.3) (23 Jul 2020)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/0.8.2...0.8.3)

First Beta release.

This version had a strong focus on testing coverage (40%->85%).

Changes compared to 0.8.2.alpha1:
* Corrections and optimisations
* new option `pamOtherPwd` to permit PAM authentication with multiple steps. 
* Rely on 0.8.2 specification (was 0.8.1)


## [0.8.2](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/0.8.2) (08 May 2020)
[Full Changelog](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/compare/0.8.1...0.8.2)
second Alpha release

## [0.8.1](https://github.com/mariadb-corporation/mariadb-connector-r2dbc/tree/0.8.1) (23 Mar. 2020)
First Alpha release
